import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MiniAuctionApiService } from '../../services/mini-auction-api.service';
import { ConfirmDialogComponent } from '../../dialogs/confirm/confirm-dialog.component';

@Component({ selector: 'app-mini-auction', templateUrl: './mini-auction.component.html', styleUrls: ['./mini-auction.component.css'] })
export class MiniAuctionComponent implements OnInit {
  session: any = null;
  candidates: any[] = [];
  selected = new Set<number>();
  label = 'Mini asta dopo mercato di riparazione';
  sourceSessionCode = '';
  sourceDate = '';
  query = '';
  involvedParticipantIds = new Set<number>();
  sources: any[] = [];
  selectedSource: any = null;
  loadingCandidates = false;
  busy = false;
  error = '';
  loaded = false;
  constructor(private api: MiniAuctionApiService, private dialog: MatDialog) {}
  ngOnInit() {
    this.load();
  }
  load() {
    this.loaded = false;
    this.api.current().subscribe({ next: s => { this.session = s; this.loaded = true; }, error: () => { this.error = 'Impossibile caricare la mini asta'; } });
    this.api.sources().subscribe({
      next: sources => { this.sources = sources; this.chooseSource(sources[0] || null); },
      error: () => { this.error = 'Impossibile caricare le aste di provenienza'; }
    });
  }
  chooseSource(source: any) {
    this.selectedSource = source;
    this.sourceSessionCode = source?.sessionCode || '';
    this.sourceDate = source?.date || '';
    this.candidates = [];
    this.selected.clear();
    this.involvedParticipantIds.clear();
    this.query = '';
    if (!source) { this.loadingCandidates = false; return; }
    this.loadingCandidates = true;
    const code = this.sourceSessionCode, date = this.sourceDate;
    this.api.candidates(code, date).subscribe({
      next: rows => {
        if (this.sourceSessionCode !== code || this.sourceDate !== date) return;
        this.candidates = rows; this.loadingCandidates = false;
      },
      error: () => {
        if (this.sourceSessionCode !== code || this.sourceDate !== date) return;
        this.error = 'Impossibile caricare gli acquisti verificati'; this.loadingCandidates = false;
      }
    });
  }
  get participants(): any[] {
    return this.candidates.filter((r, i, rows) => rows.findIndex(x => x.participantId === r.participantId) === i);
  }
  get filtered(): any[] {
    return this.candidates.filter(r => this.involvedParticipantIds.has(r.participantId)
      && (!this.query || (r.player + ' ' + r.team).toLowerCase().includes(this.query.toLowerCase())));
  }
  get involvedParticipants(): any[] {
    return this.participants.filter(p => this.involvedParticipantIds.has(p.participantId));
  }
  playersFor(participantId: number): any[] {
    return this.filtered.filter(r => r.participantId === participantId);
  }
  toggleParticipant(participantId: number, checked: boolean) {
    if (checked) {
      this.involvedParticipantIds.add(participantId);
    } else {
      this.involvedParticipantIds.delete(participantId);
      this.candidates.filter(r => r.participantId === participantId).forEach(r => this.selected.delete(r.id));
    }
  }
  toggle(row: any, checked: boolean) {
    const entries = row.role === 'PORTIERE'
      ? this.candidates.filter(r => r.participantId === row.participantId && r.role === 'PORTIERE') : [row];
    entries.forEach(r => checked ? this.selected.add(r.id) : this.selected.delete(r.id));
  }
  get outstanding(): number { return this.session?.slots?.filter((s: any) => !s.filled).length || 0; }
  prepare() {
    if (!this.selected.size || this.loadingCandidates || !this.selectedSource) return;
    this.busy = true; this.error = '';
    this.api.prepare({ label: this.label, sourceSessionCode: this.sourceSessionCode, sourceDate: this.sourceDate,
      rosterIds: Array.from(this.selected) }).subscribe({
      next: s => { this.session = s; this.busy = false; }, error: e => this.fail(e)
    });
  }
  activate() {
    this.dialog.open(ConfirmDialogComponent, { width: '520px', data: {
      title: 'Conferma cessioni definitive',
      message: 'I giocatori elencati saranno tolti dalle rose e il costo pagato sarà rimborsato. Ogni slot dovrà essere riempito al suo minimo, anche se il partecipante perde l’asta desiderata. Confermi tutte le cessioni?'
    } }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.busy = true; this.error = '';
      this.api.activate(this.session.id).subscribe({ next: s => { this.session = s; this.busy = false; }, error: e => this.fail(e) });
    });
  }
  finish() {
    this.busy = true; this.error = '';
    this.api.finish(this.session.id).subscribe({ next: () => { this.busy = false; this.selected.clear(); this.involvedParticipantIds.clear(); this.load(); }, error: e => this.fail(e) });
  }
  private fail(e: any) { this.busy = false; this.error = e.error?.error || 'Operazione non riuscita'; }
}
