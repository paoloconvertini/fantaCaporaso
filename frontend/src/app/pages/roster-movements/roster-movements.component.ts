import { Component, OnInit } from '@angular/core';
import { AdminApiService } from '../../services/admin-api.service';
import { UserApiService } from '../../services/user-api.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfirmDialogComponent } from '../../dialogs/confirm/confirm-dialog.component';

@Component({
  selector: 'app-roster-movements',
  templateUrl: './roster-movements.component.html',
  styleUrls: ['./roster-movements.component.css']
})
export class RosterMovementsComponent implements OnInit {
  movements: any[] = [];
  participants: any[] = [];
  query = '';
  type = '';
  participantId: number | null = null;
  showReverted = false;
  loading = false;
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor(private adminApi: AdminApiService, private userApi: UserApiService,
              private dialog: MatDialog, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.userApi.getAllParticipants().subscribe({ next: rows => this.participants = rows });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.adminApi.getRosterMovements({
      q: this.query.trim() || undefined,
      type: this.type || undefined,
      participantId: this.participantId || undefined,
      includeReverted: this.showReverted
    }).subscribe({
      next: rows => { this.movements = rows; this.loading = false; },
      error: () => { this.movements = []; this.loading = false; }
    });
  }

  get activeMovementCount(): number {
    return this.movements.filter(row => !row.revertedAt).length;
  }

  get revertedMovementCount(): number {
    return this.movements.filter(row => !!row.revertedAt).length;
  }

  onSearch(): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 250);
  }

  typeLabel(type: string): string {
    return ({ RELEASE: 'Svincolo', DEPARTED: 'Partito', EXCHANGE: 'Trasferimento', PURCHASE: 'Acquisto asta', MINI_PURCHASE: 'Acquisto mini asta' } as any)[type] || type;
  }

  revert(row: any): void {
    if (!row.canRevert || row.revertedAt) return;
    this.dialog.open(ConfirmDialogComponent, {
      width: '390px',
      data: {
        title: 'Annulla movimento',
        message: row.type === 'PURCHASE' || row.type === 'MINI_PURCHASE'
          ? `Annullare l’acquisto di ${row.playerName}? Verranno liberati tutti i calciatori dell’operazione e restituiti i crediti pagati. Nella mini asta lo slot torna da riempire.`
          : `Vuoi ripristinare ${row.playerName} e annullare l’intera operazione collegata?`
      }
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.adminApi.revertRosterMovement(row.id).subscribe({
        next: () => { this.snackBar.open('Movimento annullato', 'Chiudi', { duration: 3000 }); this.load(); },
        error: error => this.snackBar.open(error?.error?.message || error?.error?.error || 'Revert non riuscito', 'Chiudi', { duration: 4000 })
      });
    });
  }
}
