import { Component, OnInit } from '@angular/core';
import {UserApiService} from "../../services/user-api.service";

interface Player {
    id: number;
    name: string;
    role: string;   // PORTIERE, DIFENSORE, CENTROCAMPISTA, ATTACCANTE
    amount: number;
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

    participants: { id: number, name: string }[] = [];
    table: any[] = []; // righe della tabella pivotata

    rolesOrder = ['PORTIERE','DIFENSORE','CENTROCAMPISTA','ATTACCANTE'];

    constructor(private api: UserApiService) {}

    ngOnInit(): void {
        this.loadRosters();
    }

    loadRosters() {
        this.loading = true;
        this.api.getRosters().subscribe({
            next: (data: Player[]) => {
                this.buildTable(data);
                this.loading = false;
            },
            error: (err) => {
                console.error('Errore caricamento rosters', err);
                this.loading = false;
            }
        });
    }

    private buildTable(rosters: Player[]) {
        // 1. prendo i partecipanti
        const participantsMap = new Map<number,string>();
        rosters.forEach(r => {
            if (!participantsMap.has(r.participantId)) {
                participantsMap.set(r.participantId, r.participantName);
            }
        });
        this.participants = Array.from(participantsMap.entries())
            .map(([id,name]) => ({ id, name }));

        // 2. raggruppo per ruolo → lista di giocatori ordinati per partecipante
        const groupedByRole: { [role: string]: { [pid: number]: Player[] } } = {};
        rosters.forEach(p => {
            const role = p.role.toUpperCase();
            if (!groupedByRole[role]) groupedByRole[role] = {};
            if (!groupedByRole[role][p.participantId]) groupedByRole[role][p.participantId] = [];
            groupedByRole[role][p.participantId].push(p);
        });

        // ordino ogni lista di giocatori per amount decrescente
        Object.values(groupedByRole).forEach(byTeam =>
            Object.values(byTeam).forEach(list =>
                list.sort((a,b) => b.amount - a.amount)
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

            // riga totale crediti per ruolo
            const totals: any = { role, index: 'TOT', players: {} };
            this.participants.forEach(p => {
                const total = (groupedByRole[role]?.[p.id] || [])
                    .reduce((sum,pl) => sum + (pl.amount||0), 0);
                totals.players[p.id] = { name: '', amount: total };
            });
            this.table.push(totals);
        });
    }

    tableByRole(role: string) {
        return this.table.filter(r => r.role === role);
    }

}
