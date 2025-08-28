import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../services/api.service';

type ParticipantSummary = {
    id: number;
    name: string;
    remainingCredits: number;
    takenPortieri: number;
    takenDifensori: number;
    takenCentrocampisti: number;
    takenAttaccanti: number;
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
                // 🔹 Mappiamo i dati del BE nel formato atteso
                this.participants = res.map(p => ({
                    id: p.id,
                    name: p.name,
                    remainingCredits: p.remainingCredits,
                    takenPortieri: p.roleCounts?.PORTIERE || 0,
                    takenDifensori: p.roleCounts?.DIFENSORE || 0,
                    takenCentrocampisti: p.roleCounts?.CENTROCAMPISTA || 0,
                    takenAttaccanti: p.roleCounts?.ATTACCANTE || 0
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
