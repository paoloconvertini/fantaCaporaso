import { Component, OnInit } from '@angular/core';
import { RosterDto } from '../../../models/roster.dto';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MercatoService } from '../../../services/mercato.service';
import { AuthService } from '../../../services/auth.service';
import { RosterService } from '../../../services/roster.service';
import { ConfirmDialogComponent } from '../../../dialogs/confirm/confirm-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { UserApiService } from '../../../services/user-api.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'app-rosa',
    templateUrl: './rosa.component.html',
    styleUrls: ['./rosa.component.css']
})
export class RosaComponent implements OnInit {
    isAdmin = false;
    mercatoAttivo = false;
    participants: { id: number; name: string; username?: string }[] = [];
    selectedParticipantId?: number;
    residui: number = 0;
    roster: RosterDto[] = [];
    filteredRoster: RosterDto[] = [];
    selectedRole: string | null = null;
    loading = false;

    get displayedColumns(): string[] {
        return this.isAdmin ? ['team', 'player', 'amount', 'actions'] : ['team', 'player', 'amount'];
    }

    roles = [
        { code: 'PORTIERE', label: 'POR' },
        { code: 'DIFENSORE', label: 'DIF' },
        { code: 'CENTROCAMPISTA', label: 'CEN' },
        { code: 'ATTACCANTE', label: 'ATT' }
    ];

    constructor(
        private rosterService: RosterService,
        private mercatoService: MercatoService,
        private snackBar: MatSnackBar,
        private auth: AuthService,
        private dialog: MatDialog,
        private userApi: UserApiService,
        private route: ActivatedRoute
    ) {}

    ngOnInit(): void {
        this.isAdmin = this.auth.hasRole('admin');
        this.loadMercatoStatus();
        const requestedParticipantId = Number(this.route.snapshot.queryParamMap.get('participantId')) || undefined;

        if (this.isAdmin) {
            this.selectedParticipantId = requestedParticipantId;
            this.loadParticipants();
            if (requestedParticipantId) this.loadRoster();
        } else if (requestedParticipantId) {
            this.selectedParticipantId = requestedParticipantId;
            this.loadRoster();
        } else {
            this.loadCurrentParticipantRoster();
        }
    }

    /** 🔹 Carica stato mercato */
    loadMercatoStatus(): void {
        this.mercatoService.getConfig().subscribe({
            next: (cfg) => (this.mercatoAttivo = !!cfg?.attiva),
            error: () => (this.mercatoAttivo = false)
        });
    }

    /** 🔹 Admin: carica lista partecipanti */
    loadParticipants(): void {
        this.loading = true;
        this.rosterService.getGroupedRosters().subscribe({
            next: (res) => {
                this.participants = res.map((p) => ({
                    id: p.participantId,
                    name: p.participantName,
                    username: p.username
                }));
                this.loading = false;
            },
            error: () => {
                this.loading = false;
                this.snackBar.open('Errore nel caricamento dei partecipanti', 'Chiudi', { duration: 3000 });
            }
        });
    }

    /** 🔹 Carica la rosa (admin o utente corrente) */
    loadRoster(): void {
        if (this.isAdmin && !this.selectedParticipantId) {
            this.filteredRoster = [];
            this.residui = 0;
            return;
        }

        this.loading = true;
        this.rosterService.getMyRoster(this.selectedParticipantId).subscribe({
            next: (res) => {
                this.roster = [...res].sort((a, b) => (b.amount ?? 0) - (a.amount ?? 0));
                this.selectedRole = 'PORTIERE';
                this.filterByRole(this.selectedRole);
                this.residui = res?.length ? res[0].residui ?? 0 : 0;
                this.loading = false;
            },
            error: () => {
                this.roster = [];
                this.filteredRoster = [];
                this.residui = 0;
                this.loading = false;
                this.snackBar.open('Errore nel caricamento della rosa', 'Chiudi', { duration: 3000 });
            }
        });
    }

    loadCurrentParticipantRoster(): void {
        this.loading = true;
        this.userApi.getCurrentParticipant().subscribe({
            next: (participant: any) => {
                this.selectedParticipantId = participant?.id;
                this.loadRoster();
            },
            error: () => {
                this.loading = false;
                this.snackBar.open('Partecipante non trovato per l’utente corrente', 'Chiudi', { duration: 3000 });
            }
        });
    }

    /** 🔹 Filtra per ruolo */
    filterByRole(role: string | null): void {
        this.selectedRole = role;
        this.filteredRoster = role
            ? this.roster.filter(r => r.role?.toUpperCase() === role.toUpperCase())
            : this.roster;
    }

    /** 🔹 Conta giocatori per ruolo */
    getCountForRole(role: string): string {
        const count = this.roster.filter((r) => r.role === role).length;
        return count.toString();
    }

    /** 🔹 Conferma svincolo */
    confermaSvincolo(player: RosterDto): void {
        if (!this.isAdmin) return;
        const dialogRef = this.dialog.open(ConfirmDialogComponent, {
            width: '350px',
            data: {
                title: 'Conferma svincolo',
                message: `Vuoi davvero svincolare ${player.playerName}?`
            }
        });

        dialogRef.afterClosed().subscribe(result => {
            if (result === true) {
                this.svincola(player.playerId);
            }
        });
    }

    /** 🔹 Esegue svincolo */
    svincola(playerId: number): void {
        if (!this.isAdmin) return;
        if (!this.mercatoAttivo) {
            this.snackBar.open('Mercato chiuso: impossibile svincolare', 'Chiudi', { duration: 2500 });
            return;
        }

        const participantId = this.selectedParticipantId ?? this.roster[0]?.participantId;
        if (!participantId) return;

        this.rosterService.svincola(participantId, playerId).subscribe({
            next: () => {
                this.snackBar.open('Giocatore svincolato', 'Chiudi', { duration: 2000 });
                this.loadRoster(); // 🔁 ricarica dal backend per aggiornare residui
            }
        });
    }
}
