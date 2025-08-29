import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../services/api.service';

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
    participants: ParticipantSummary[] = [];
    loading = true;
    error = '';

    constructor(private api: ApiService) {}

    ngOnInit(): void {
        this.load();
    }

    load() {
        this.api.getSummary().subscribe({
            next: (res: any[]) => {
                // 🔹 adatto il JSON del BE
                this.participants = res.map(p => ({
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
                }));
                this.loading = false;
            },
            error: () => {
                this.error = 'Errore nel caricamento dati';
                this.loading = false;
            }
        });
    }
}
