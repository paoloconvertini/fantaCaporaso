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
  users: any[] = [];
  loading = false;
  saving = false;

  get configuredParticipants(): number {
    return this.users.filter(user => !!user.participantId).length;
  }

  get unconfiguredParticipants(): ParticipantOption[] {
    const configured = new Set(this.users.map(user => Number(user.participantId)).filter(Boolean));
    return this.participants.filter(participant => !configured.has(participant.id));
  }

  constructor(
    private fb: FormBuilder,
    private adminApi: AdminApiService,
    private userApi: UserApiService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['fanta2026', [Validators.required, Validators.minLength(4)]],
      permanentPassword: [true],
      observer: [false],
      participantId: [null],
      participantName: [''],
      totalCredits: [500, [Validators.required, Validators.min(1)]]
    });

    this.loadParticipants();
    this.loadUsers();
  }

  createUser(): void {
    const observer = !!this.form.value.observer;
    if (this.form.invalid || (!observer && !this.form.value.participantId && !this.form.value.participantName?.trim())) {
      this.form.markAllAsTouched();
      this.snackBar.open('Compila tutti i campi richiesti', 'Chiudi', { duration: 3000 });
      return;
    }

    this.saving = true;
    const payload = observer
      ? { ...this.form.value, participantId: null, participantName: null, totalCredits: null }
      : this.form.value;
    this.adminApi.createUser(payload).subscribe({
      next: () => {
        this.snackBar.open('Utente creato', 'Chiudi', { duration: 2500 });
        this.form.reset();
        this.form.patchValue({ totalCredits: 500, password: 'fanta2026', permanentPassword: true, observer: false });
        this.loadParticipants();
        this.loadUsers();
        this.saving = false;
      },
      error: (err) => {
        this.saving = false;
        const message = err?.error?.message || err?.error?.error || 'Errore creazione utente';
        this.snackBar.open(message, 'Chiudi', { duration: 4000 });
      }
    });
  }

  loadParticipants(): void {
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

  loadUsers(): void {
    this.adminApi.getUsers().subscribe({
      next: users => this.users = users,
      error: () => this.snackBar.open('Errore caricamento utenti', 'Chiudi', { duration: 3000 })
    });
  }
}
