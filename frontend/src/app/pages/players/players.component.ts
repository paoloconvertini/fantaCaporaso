import { Component, OnInit } from '@angular/core';
import {UserApiService} from "../../services/user-api.service";
import { AuthService } from '../../services/auth.service';
import { MatDialog } from '@angular/material/dialog';
import { EditPlayerValueDialogComponent } from '../../dialogs/edit-player-value-dialog.component';

@Component({
    selector: 'app-players',
    templateUrl: './players.component.html',
    styleUrls: ['./players.component.css']
})
export class PlayersComponent implements OnInit {
    players: any[] = [];
    roles: string[] = ['PORTIERE', 'DIFENSORE', 'CENTROCAMPISTA', 'ATTACCANTE'];
    selectedRole: string | null = null;
    searchQuery = '';
    loading = false;
    isAdmin = false;
    private searchTimer?: ReturnType<typeof setTimeout>;

    displayedColumns = ['name', 'team', 'role', 'valore'];

    constructor(private api: UserApiService, public auth: AuthService, private dialog: MatDialog) {}

    ngOnInit(): void {
        this.isAdmin = this.auth.hasRole('admin');
        this.loadPlayers();
    }

    loadPlayers(): void {
        this.loading = true;
        let params: any = {};
        if (this.selectedRole) params.role = this.selectedRole;
        if (this.searchQuery.trim()) params.q = this.searchQuery.trim();

        this.api.getPlayers(params).subscribe({
            next: data => {
                this.players = data;
                this.loading = false;
            },
            error: () => {
                this.players = [];
                this.loading = false;
            }
        });
    }

    onRoleChange(): void {
        this.loadPlayers();
    }

    setRole(role: string | null): void {
        this.selectedRole = role;
        this.loadPlayers();
    }

    onSearchChange(): void {
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => this.loadPlayers(), 250);
    }

    editValue(player: any): void {
        if (!this.isAdmin) return;
        this.dialog.open(EditPlayerValueDialogComponent, {
            data: { id: player.id, name: player.name, team: player.team, value: player.valore }
        }).afterClosed().subscribe(value => {
            if (typeof value === 'number') this.loadPlayers();
        });
    }

    roleLabel(role: string): string {
        const labels: Record<string, string> = {
            PORTIERE: 'POR',
            DIFENSORE: 'DIF',
            CENTROCAMPISTA: 'CEN',
            ATTACCANTE: 'ATT'
        };
        return labels[role] || role;
    }
}
