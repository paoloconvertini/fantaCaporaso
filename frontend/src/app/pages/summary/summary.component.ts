import { Component, Input, OnInit } from '@angular/core';
import { ApiService } from '../../services/api.service';

type RoleKey = 'PORTIERE' | 'DIFENSORE' | 'CENTROCAMPISTA' | 'ATTACCANTE';

type ParticipantSummary = {
    id: number;
    name: string;
    totalCredits: number;
    spentCredits: number;
    remainingCredits: number;
    roleCounts: {
        PORTIERE: number;
        DIFENSORE: number;
        CENTROCAMPISTA: number;
        ATTACCANTE: number;
    };
};

@Component({
    selector: 'app-summary',
    templateUrl: './summary.component.html',
    styleUrls: ['./summary.component.css']
})
export class SummaryComponent implements OnInit {
    /** Ruolo su cui filtrare la colonna (se null/'' mostra tutte) */
    @Input() roleFilter: RoleKey | '' | null = null;
    /** Evidenzia questa riga (id del “mio” participant, es. Mobile) */
    @Input() selfId: number | null = null;

    participants: ParticipantSummary[] = [];
    loading = true;
    error = '';

    constructor(private api: ApiService) {}

    ngOnInit(): void {
        this.load();

        // ricarica quando il BE segnala aggiornamenti
        this.api.summaryUpdated$.subscribe(() => this.load());
    }

    private sortParticipants(list: ParticipantSummary[]): ParticipantSummary[] {
        return [...list].sort((a, b) => {
            const d = b.remainingCredits - a.remainingCredits;
            return d !== 0 ? d : a.name.localeCompare(b.name);
        });
    }

    load() {
        this.loading = true;
        this.api.getSummary().subscribe({
            next: (res: any[]) => {
                const mapped = res.map(p => ({
                    id: p.id,
                    name: p.name,
                    totalCredits: p.totalCredits,
                    spentCredits: p.spentCredits,
                    remainingCredits: p.remainingCredits,
                    roleCounts: {
                        PORTIERE: p.roleCounts?.PORTIERE || 0,
                        DIFENSORE: p.roleCounts?.DIFENSORE || 0,
                        CENTROCAMPISTA: p.roleCounts?.CENTROCAMPISTA || 0,
                        ATTACCANTE: p.roleCounts?.ATTACCANTE || 0
                    }
                })) as ParticipantSummary[];

                this.participants = this.sortParticipants(mapped);
                this.loading = false;
            },
            error: () => {
                this.error = 'Errore nel caricamento dati';
                this.loading = false;
            }
        });
    }

    isSelf(p: ParticipantSummary): boolean {
        return this.selfId != null && p.id === this.selfId;
    }

    abbrev(role: RoleKey): 'P' | 'D' | 'C' | 'A' {
        return (role === 'PORTIERE' ? 'P' :
            role === 'DIFENSORE' ? 'D' :
                role === 'CENTROCAMPISTA' ? 'C' : 'A') as any;
    }

    // Abbreviazione del ruolo filtrato (per la label "P/D/C/A")
    get roleAbbrev(): string {
        switch (this.roleFilter) {
            case 'PORTIERE': return 'P';
            case 'DIFENSORE': return 'D';
            case 'CENTROCAMPISTA': return 'C';
            case 'ATTACCANTE': return 'A';
            default: return '';
        }
    }

// Conteggio per il ruolo filtrato, senza usare cast in template
    countForFilter(p: ParticipantSummary): number {
        if (!this.roleFilter) return 0;
        return p.roleCounts[this.roleFilter];
    }

}
