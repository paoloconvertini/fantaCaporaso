import { Component, OnInit } from '@angular/core';
import {UserApiService} from "../../../services/user-api.service";

@Component({
    selector: 'app-mobile-rosters',
    templateUrl: './mobile-rosters.component.html',
    styleUrls: ['./mobile-rosters.component.css']
})
export class MobileRostersComponent implements OnInit {
    rosters: any[] = [];
    participants: string[] = [];
    selectedParticipant: string | null = null;

    constructor(private api: UserApiService) {}

    ngOnInit(): void {
        this.loadRosters();
    }

    loadRosters(): void {
        this.api.getRosters(this.selectedParticipant || undefined).subscribe({
            next: data => {
                this.rosters = data;
                this.participants = Array.from<string>(new Set(data.map((r: any) => r.participant))).sort();
            },
            error: err => console.error('Errore caricamento rosters', err)
        });
    }
}
