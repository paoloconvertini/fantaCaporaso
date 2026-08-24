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
    searchQuery = '';
    private searchTimer?: ReturnType<typeof setTimeout>;

    constructor(private api: UserApiService) {}

    ngOnInit(): void {
        this.loadPlayers();
    }

    loadPlayers(): void {
        let params: any = {};
        if (this.selectedRole) params.role = this.selectedRole;
        if (this.searchQuery.trim()) params.q = this.searchQuery.trim();

        this.api.getPlayers(params).subscribe({
            next: data => this.players = data
        });
    }

    onSearchChange(): void {
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => this.loadPlayers(), 250);
    }
}
