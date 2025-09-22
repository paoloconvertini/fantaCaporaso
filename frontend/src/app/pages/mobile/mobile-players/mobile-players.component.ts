import { Component, OnInit } from '@angular/core';
import {UserApiService} from "../../../services/user-api.service";

@Component({
    selector: 'app-mobile-players',
    templateUrl: './mobile-players.component.html',
    styleUrls: ['./mobile-players.component.css']
})
export class MobilePlayersComponent implements OnInit {
    players: any[] = [];
    roles: string[] = ['PORTIERE', 'DIFENSORE', 'CENTROCAMPISTA', 'ATTACCANTE'];
    selectedRole: string | null = null;

    constructor(private api: UserApiService) {}

    ngOnInit(): void {
        this.loadPlayers();
    }

    loadPlayers(): void {
        let params: any = {};
        if (this.selectedRole) params.role = this.selectedRole;

        this.api.getPlayers(params).subscribe({
            next: data => this.players = data,
            error: err => console.error('Errore caricamento players', err)
        });
    }
}
