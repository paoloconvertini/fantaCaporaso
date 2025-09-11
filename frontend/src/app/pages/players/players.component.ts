import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../services/api.service';

@Component({
    selector: 'app-players',
    templateUrl: './players.component.html',
    styleUrls: ['./players.component.css']
})
export class PlayersComponent implements OnInit {
    players: any[] = [];
    roles: string[] = ['PORTIERE', 'DIFENSORE', 'CENTROCAMPISTA', 'ATTACCANTE'];
    selectedRole: string | null = null;
    loading = false;

    displayedColumns = ['name', 'team', 'role', 'valore'];

    // === upload (solo admin) ===
    isAdmin = true; // 🔑 per ora fisso, meglio check da login o localStorage
    selectedFile: File | null = null;
    uploadResult: any = null;
    adminPin: string = '1234';

    constructor(private api: ApiService) {}

    ngOnInit(): void {
        this.loadPlayers();
    }

    loadPlayers(): void {
        this.loading = true;
        let params: any = {};
        if (this.selectedRole) params.role = this.selectedRole;

        this.api.getPlayers(params).subscribe({
            next: data => {
                this.players = data;
                this.loading = false;
            },
            error: err => {
                console.error('Errore caricamento players', err);
                this.loading = false;
            }
        });
    }

    onRoleChange(): void {
        this.loadPlayers();
    }
}
