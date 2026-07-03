import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../services/admin-api.service';
import { UserApiService } from '../../services/user-api.service';

type ParticipantOption = {
  id: number;
  name: string;
};

@Component({
  selector: 'app-admin-users',
  templateUrl: './admin-users.component.html',
  styleUrls: ['./admin-users.component.css']
})
export class AdminUsersComponent implements OnInit {
  form!: FormGroup;
  participants: ParticipantOption[] = [];
  loading = false;
  saving = false;

  constructor(
    private fb: FormBuilder,
    private adminApi: AdminApiService,
    private userApi: UserApiService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(4)]],
      participantId: [null, Validators.required]
    });

    this.loadParticipants();
  }

  createUser(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.snackBar.open('Compila tutti i campi richiesti', 'Chiudi', { duration: 3000 });
      return;
    }

    this.saving = true;
    this.adminApi.createKeycloakUser(this.form.value).subscribe({
      next: () => {
        this.snackBar.open('Utente creato', 'Chiudi', { duration: 2500 });
        this.form.reset();
        this.saving = false;
      },
      error: (err) => {
        this.saving = false;
        const message = err?.error?.message || err?.error?.error || 'Errore creazione utente';
        this.snackBar.open(message, 'Chiudi', { duration: 4000 });
      }
    });
  }

  private loadParticipants(): void {
    this.loading = true;
    this.userApi.getAllParticipants().subscribe({
      next: (participants: any[]) => {
        this.participants = participants
          .map(p => ({ id: p.id, name: p.name }))
          .sort((a, b) => a.name.localeCompare(b.name));
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Errore caricamento squadre', 'Chiudi', { duration: 3000 });
      }
    });
  }
}
