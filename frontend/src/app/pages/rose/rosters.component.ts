import { Component, OnInit } from '@angular/core';
import {UserApiService} from "../../services/user-api.service";
import { forkJoin } from 'rxjs';
import { AdminApiService } from '../../services/admin-api.service';
import { AuthService } from '../../services/auth.service';
import { MatSnackBar } from '@angular/material/snack-bar';

interface Player {
    playerId: number;
    playerName: string;
    role: string;   // PORTIERE, DIFENSORE, CENTROCAMPISTA, ATTACCANTE
    amount: number;
    valore: number;
    participantId: number;
    participantName: string;
}

@Component({
    selector: 'app-rosters',
    templateUrl: './rosters.component.html',
    styleUrls: ['./rosters.component.css']
})
export class RostersComponent implements OnInit {
    loading = false;
    isAdmin = false;

    participants: { id: number, name: string, username?: string, remainingCredits?: number }[] = [];
    table: any[] = []; // righe della tabella pivotata
    rosterByParticipantRole: { [participantId: number]: { [role: string]: Player[] } } = {};

    rolesOrder = ['PORTIERE','DIFENSORE','CENTROCAMPISTA','ATTACCANTE'];

    constructor(private api: UserApiService,
                private adminApi: AdminApiService,
                private auth: AuthService,
                private snackBar: MatSnackBar) {}

    ngOnInit(): void {
        this.isAdmin = this.auth.hasRole('admin');
        this.loadRosters();
    }

    exportRosters(): void {
        this.adminApi.exportRostersExcel().subscribe({
            next: blob => {
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = `rose_fantamaster_${new Date().toISOString().slice(0, 10)}.xlsx`;
                link.click();
                URL.revokeObjectURL(url);
            },
            error: () => this.snackBar.open('Errore esportazione rose', 'Chiudi', { duration: 3500 })
        });
    }

    loadRosters() {
        this.loading = true;
        forkJoin({
            participants: this.api.getAllParticipants(),
            rosters: this.api.getRosters()
        }).subscribe({
            next: ({ participants, rosters }) => {
                this.buildTable(rosters, participants);
                this.loading = false;
            },
            error: () => {
                this.participants = [];
                this.table = [];
                this.loading = false;
            }
        });
    }

    private buildTable(rosters: Player[], participants: { id: number; name: string; username?: string; remainingCredits?: number }[]) {
        this.rosterByParticipantRole = {};

        // 1. mostro sempre tutti i partecipanti, anche senza giocatori assegnati.
        this.participants = [...participants]
            .map(p => ({
                id: p.id,
                name: p.name,
                username: p.username,
                remainingCredits: p.remainingCredits ?? 0
            }))
            .sort((a, b) => a.name.localeCompare(b.name));

        // 2. raggruppo per ruolo → lista di giocatori ordinati per partecipante
        const groupedByRole: { [role: string]: { [pid: number]: Player[] } } = {};
        rosters.forEach(p => {
            const role = p.role.toUpperCase();
            if (!groupedByRole[role]) groupedByRole[role] = {};
            if (!groupedByRole[role][p.participantId]) groupedByRole[role][p.participantId] = [];
            groupedByRole[role][p.participantId].push(p);

            if (!this.rosterByParticipantRole[p.participantId]) this.rosterByParticipantRole[p.participantId] = {};
            if (!this.rosterByParticipantRole[p.participantId][role]) this.rosterByParticipantRole[p.participantId][role] = [];
            this.rosterByParticipantRole[p.participantId][role].push(p);
        });

        // Mantengo i reparti separati e ordino alfabeticamente i giocatori.
        Object.values(groupedByRole).forEach(byTeam =>
            Object.values(byTeam).forEach(list =>
                list.sort((a, b) =>
                    (a.playerName ?? '').localeCompare(b.playerName ?? '', 'it', { sensitivity: 'base' })
                )
            )
        );
        Object.values(this.rosterByParticipantRole).forEach(byRole =>
            Object.values(byRole).forEach(list =>
                list.sort((a, b) =>
                    (a.playerName ?? '').localeCompare(b.playerName ?? '', 'it', { sensitivity: 'base' })
                )
            )
        );

        // 3. costruisco righe tabella
        this.table = [];

        this.rolesOrder.forEach(role => {
            const maxLen = Math.max(
                ...this.participants.map(p => (groupedByRole[role]?.[p.id]?.length || 0))
            );

            for (let i = 0; i < maxLen; i++) {
                const row: any = { role, index: i+1, players: {} };
                this.participants.forEach(p => {
                    row.players[p.id] = groupedByRole[role]?.[p.id]?.[i] || null;
                });
                this.table.push(row);
            }

            // riga totale crediti e quotazioni per ruolo
            const totals: any = { role, index: 'TOT', players: {} };
            this.participants.forEach(p => {
                const total = (groupedByRole[role]?.[p.id] || [])
                    .reduce((sum,pl) => sum + (pl.amount||0), 0);
                const valore = (groupedByRole[role]?.[p.id] || [])
                    .reduce((sum, pl) => sum + (pl.valore || 0), 0);
                totals.players[p.id] = { name: '', amount: total, valore };
            });
            this.table.push(totals);
        });
    }

    tableByRole(role: string) {
        return this.table.filter(r => r.role === role);
    }

    playersFor(participantId: number, role: string): Player[] {
        return this.rosterByParticipantRole[participantId]?.[role] || [];
    }

    totalFor(participantId: number, role: string): number {
        return this.playersFor(participantId, role)
            .reduce((sum, player) => sum + (player.amount || 0), 0);
    }

    totalSpent(participantId: number): number {
        return this.rolesOrder
            .reduce((sum, role) => sum + this.totalFor(participantId, role), 0);
    }

    marketValueFor(participantId: number, role: string): number {
        return this.playersFor(participantId, role)
            .reduce((sum, player) => sum + (player.valore || 0), 0);
    }

    totalMarketValue(participantId: number): number {
        return this.rolesOrder
            .reduce((sum, role) => sum + this.marketValueFor(participantId, role), 0);
    }

}
