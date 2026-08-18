import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {UserApiService} from "../services/user-api.service";
import {AdminApiService} from "../services/admin-api.service";

@Component({
    selector: 'app-manual-assign-dialog',
    templateUrl: './manual-assign-dialog.component.html',
    styleUrls: ['./manual-assign-dialog.component.css']
})
export class ManualAssignDialogComponent {
    participants: any[] = [];
    players: any[] = [];
    selectedPlayer: any | null = null;
    query = '';
    searching = false;
    selectedParticipantId: number | null = null;
    amount: number;

    constructor(
        private api: UserApiService,
        private adminApi: AdminApiService,
        private dialogRef: MatDialogRef<ManualAssignDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: any
    ) {
        this.amount = data?.role === 'PORTIERE' ? 3 : 1;
        this.api.getParticipants().subscribe(res => this.participants = res);
        if (data?.player) {
            this.query = data.player;
            this.search(this.query, true);
        }
    }

    get minimumAmount(): number {
        return this.selectedPlayer?.role === 'PORTIERE' ? 3 : 1;
    }

    search(value: string, selectExact = false) {
        this.query = value || '';
        this.selectedPlayer = null;
        this.selectedParticipantId = null;
        if (this.query.trim().length < 2) {
            this.players = [];
            return;
        }
        this.searching = true;
        this.adminApi.searchPlayers(this.query.trim()).subscribe({
            next: players => {
                this.players = players;
                this.searching = false;
                if (selectExact) {
                    const exact = players.find(player =>
                        player.name === this.data.player && player.team === this.data.team);
                    if (exact) this.selectPlayer(exact.id);
                }
            },
            error: () => {
                this.players = [];
                this.searching = false;
            }
        });
    }

    selectPlayer(playerId: number) {
        this.selectedPlayer = this.players.find(player => player.id === playerId) || null;
        if (!this.selectedPlayer) return;
        this.selectedParticipantId = this.selectedPlayer.ownerParticipantId ?? null;
        this.amount = this.selectedPlayer.amount ?? (this.selectedPlayer.role === 'PORTIERE' ? 3 : 1);
    }

    save() {
        if (!this.selectedPlayer || !this.selectedParticipantId || this.amount < this.minimumAmount) return;
        this.dialogRef.close({
            playerId: this.selectedPlayer.id,
            participantId: this.selectedParticipantId,
            amount: this.amount
        });
    }

    cancel() {
        this.dialogRef.close();
    }
}
