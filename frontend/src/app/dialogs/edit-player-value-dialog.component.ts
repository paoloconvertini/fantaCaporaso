import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AdminApiService } from '../services/admin-api.service';

@Component({
  selector: 'app-edit-player-value-dialog',
  template: `
    <h2 mat-dialog-title>Aggiorna quotazione</h2>
    <mat-dialog-content>
      <p><strong>{{ data.name }}</strong><small>{{ data.team }}</small></p>
      <mat-form-field appearance="outline">
        <mat-label>Nuova quotazione</mat-label>
        <input matInput type="number" min="0" [(ngModel)]="value" />
      </mat-form-field>
      <p class="error" *ngIf="error">{{ error }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Annulla</button>
      <button mat-raised-button color="primary" [disabled]="saving || value < 0" (click)="save()">Salva</button>
    </mat-dialog-actions>`,
  styles: [`
    mat-dialog-content { min-width: 290px; color: #e8f3ef; }
    p { display: flex; flex-direction: column; gap: 3px; }
    p small { color: #9db3aa; text-transform: uppercase; }
    mat-form-field { width: 100%; margin-top: 10px; }
    .error { color: #ff9b9b; }
  `]
})
export class EditPlayerValueDialogComponent {
  value: number;
  saving = false;
  error = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { id: number; name: string; team: string; value: number },
    private ref: MatDialogRef<EditPlayerValueDialogComponent>,
    private api: AdminApiService
  ) { this.value = data.value || 0; }

  save(): void {
    if (this.value == null || this.value < 0) return;
    this.saving = true;
    this.api.updatePlayerValue(this.data.id, this.value).subscribe({
      next: () => this.ref.close(this.value),
      error: error => { this.error = error?.error?.error || 'Aggiornamento non riuscito'; this.saving = false; }
    });
  }
}
