import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RosterDto } from '../models/roster.dto';
import { RosterService } from '../services/roster.service';

interface SwapDialogData {
  sourceParticipantId: number;
  sourceParticipantName: string;
  sourceRoster: RosterDto[];
  participants: { id: number; name: string }[];
  initialRole: string;
}

@Component({
  selector: 'app-roster-swap-dialog',
  templateUrl: './roster-swap-dialog.component.html',
  styleUrls: ['./roster-swap-dialog.component.css']
})
export class RosterSwapDialogComponent implements OnInit {
  readonly roles = [
    { code: 'PORTIERE', label: 'POR' }, { code: 'DIFENSORE', label: 'DIF' },
    { code: 'CENTROCAMPISTA', label: 'CEN' }, { code: 'ATTACCANTE', label: 'ATT' }
  ];
  destinationParticipantId?: number;
  destinationRoster: RosterDto[] = [];
  sourceRole: string;
  destinationRole: string;
  sourceSelected = new Set<number>();
  destinationSelected = new Set<number>();
  loadingDestination = false;
  saving = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: SwapDialogData,
              private dialogRef: MatDialogRef<RosterSwapDialogComponent>,
              private rosterService: RosterService,
              private snackBar: MatSnackBar) {
    this.sourceRole = data.initialRole || 'PORTIERE';
    this.destinationRole = data.initialRole || 'PORTIERE';
  }

  ngOnInit(): void {}

  get destinations(): { id: number; name: string }[] {
    return this.data.participants.filter(row => row.id !== this.data.sourceParticipantId);
  }

  get sourceVisible(): RosterDto[] { return this.byRole(this.data.sourceRoster, this.sourceRole); }
  get destinationVisible(): RosterDto[] { return this.byRole(this.destinationRoster, this.destinationRole); }
  get balanced(): boolean { return this.sourceSelected.size > 0 && this.sourceSelected.size === this.destinationSelected.size; }

  selectDestination(): void {
    this.destinationSelected.clear();
    this.destinationRoster = [];
    if (!this.destinationParticipantId) return;
    this.loadingDestination = true;
    this.rosterService.getMyRoster(this.destinationParticipantId).subscribe({
      next: rows => { this.destinationRoster = rows; this.loadingDestination = false; },
      error: () => { this.loadingDestination = false; }
    });
  }

  toggle(player: RosterDto, side: 'source' | 'destination'): void {
    const roster = side === 'source' ? this.data.sourceRoster : this.destinationRoster;
    const selected = side === 'source' ? this.sourceSelected : this.destinationSelected;
    if (side === 'source') this.destinationRole = player.role;
    if (player.role === 'PORTIERE') {
      const goalkeeperIds = roster.filter(row => row.role === 'PORTIERE').map(row => row.playerId);
      const remove = goalkeeperIds.every(id => selected.has(id));
      goalkeeperIds.forEach(id => remove ? selected.delete(id) : selected.add(id));
      return;
    }
    selected.has(player.playerId) ? selected.delete(player.playerId) : selected.add(player.playerId);
  }

  confirm(): void {
    if (!this.destinationParticipantId || !this.balanced || this.saving) return;
    this.saving = true;
    this.rosterService.swap({
      sourceParticipantId: this.data.sourceParticipantId,
      destinationParticipantId: this.destinationParticipantId,
      sourcePlayerIds: [...this.sourceSelected],
      destinationPlayerIds: [...this.destinationSelected]
    }).subscribe({
      next: result => this.dialogRef.close(result),
      error: error => {
        this.saving = false;
        this.snackBar.open(error?.error?.error || 'Scambio non riuscito', 'Chiudi', { duration: 4500 });
      }
    });
  }

  playerLabel(player: RosterDto): string {
    return `${player.playerName} · ${player.team} · costo ${player.amount}`;
  }

  private byRole(roster: RosterDto[], role: string): RosterDto[] {
    return roster.filter(row => row.role === role)
      .sort((a, b) => a.playerName.localeCompare(b.playerName, 'it'));
  }
}
