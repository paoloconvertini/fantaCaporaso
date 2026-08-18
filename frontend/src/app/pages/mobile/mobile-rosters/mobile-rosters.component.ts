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
                const roleOrder = ['PORTIERE', 'DIFENSORE', 'CENTROCAMPISTA', 'ATTACCANTE'];
                this.rosters = [...data].sort((a: any, b: any) =>
                    (a.participant ?? '').localeCompare(b.participant ?? '', 'it', { sensitivity: 'base' })
                    || roleOrder.indexOf((a.role ?? '').toUpperCase()) - roleOrder.indexOf((b.role ?? '').toUpperCase())
                    || (a.playerName ?? '').localeCompare(b.playerName ?? '', 'it', { sensitivity: 'base' })
                );
                this.participants = Array.from<string>(new Set(data.map((r: any) => r.participant))).sort();
            }
        });
    }
}
