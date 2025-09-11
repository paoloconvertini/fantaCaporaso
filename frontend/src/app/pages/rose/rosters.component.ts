import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../services/api.service';

@Component({
    selector: 'app-rosters',
    templateUrl: './rosters.component.html',
    styleUrls: ['./rosters.component.css']
})
export class RostersComponent implements OnInit {
    rosters: RosterDto[] = [];
    participants: string[] = [];   // popolata dinamicamente
    selectedParticipant: string | null = null;
    loading = false;

    selectedFile: File | null = null;
    uploadResult: any = null;
    adminPin: string = '1234'; // ⚠️ per ora hardcoded, meglio prenderlo da settings

    displayedColumns = ['participant', 'playerName', 'team', 'role', 'amount'];

    constructor(private rosterService: ApiService) {}

    ngOnInit(): void {
        this.loadRosters();
    }

    loadRosters(): void {
        this.loading = true;
        this.rosterService.getRosters(this.selectedParticipant || undefined).subscribe({
            next: data => {
                this.rosters = data;
                this.loading = false;

                // estrai lista partecipanti unici
                this.participants = Array.from<string>(new Set(data.map(r => r.participant)));
                this.participants.sort();

            },
            error: err => {
                console.error('Errore caricamento rosters', err);
                this.loading = false;
            }
        });
    }

    onParticipantChange(): void {
        this.loadRosters();
    }

    onFileSelected(event: any): void {
        const file = event.target.files[0];
        if (file) {
            this.selectedFile = file;
        }
    }

    uploadFile(): void {
        if (!this.selectedFile) return;
        this.rosterService.uploadRosterExcel(this.selectedFile, this.adminPin).subscribe({
            next: res => {
                this.uploadResult = res;
                this.loadRosters(); // ricarica tabella aggiornata
            },
            error: err => {
                console.error('Errore upload roster Excel', err);
                this.uploadResult = { error: err.message };
            }
        });
    }

}
export interface RosterDto {
    participantName: string;
    playerName: string;
    team: string;
    role: string;
    amount: number;
}