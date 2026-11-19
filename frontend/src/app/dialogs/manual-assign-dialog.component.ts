import { Component, OnDestroy } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { Subscription, switchMap, timer } from 'rxjs';
import {AdminApiService} from "../services/admin-api.service";

@Component({
    selector: 'app-manual-assign-dialog',
    templateUrl: './manual-assign-dialog.component.html',
    styleUrls: ['./manual-assign-dialog.component.css']
})
export class ManualAssignDialogComponent implements OnDestroy {
    participants: any[] = [];
    players: any[] = [];
    selectedPlayer: any | null = null;
    query = '';
    searching = false;
    loadingParticipants = false;
    selectedParticipantId: number | null = null;
    amount = 1;
    private playerSearch?: Subscription;
    private participantSearch?: Subscription;

    constructor(
        private adminApi: AdminApiService,
        private dialogRef: MatDialogRef<ManualAssignDialogComponent>
    ) {}

    get minimumAmount(): number {
        return this.selectedPlayer?.role === 'PORTIERE' ? 3 : 1;
    }

    search(value: string) {
        this.playerSearch?.unsubscribe();
        this.participantSearch?.unsubscribe();
        this.query = value || '';
        this.players = [];
        this.participants = [];
        this.selectedPlayer = null;
        this.selectedParticipantId = null;
        this.loadingParticipants = false;
        const query = this.query.trim();
        this.searching = query.length >= 2;
        if (!this.searching) return;

        this.playerSearch = timer(300).pipe(
            switchMap(() => this.adminApi.searchPlayers(query))
        ).subscribe({
            next: players => {
                this.players = players;
                this.searching = false;
            },
            error: () => {
                this.searching = false;
            }
        });
    }

    selectPlayer(playerId: number) {
        this.participantSearch?.unsubscribe();
        this.loadingParticipants = false;
        this.selectedPlayer = this.players.find(player => player.id === playerId) || null;
        this.participants = [];
        this.selectedParticipantId = null;
        if (!this.selectedPlayer) return;
        this.amount = this.selectedPlayer.amount ?? (this.selectedPlayer.role === 'PORTIERE' ? 3 : 1);
        this.loadingParticipants = true;
        const selectedPlayerId = this.selectedPlayer.id;
        this.participantSearch = this.adminApi.getEligibleParticipants(selectedPlayerId).subscribe({
            next: participants => {
                if (this.selectedPlayer?.id !== selectedPlayerId) return;
                this.participants = participants;
                this.selectedParticipantId = this.selectedPlayer?.ownerParticipantId ?? null;
                this.loadingParticipants = false;
            },
            error: () => {
                if (this.selectedPlayer?.id !== selectedPlayerId) return;
                this.participants = [];
                this.loadingParticipants = false;
            }
        });
    }

    save() {
        if (!this.selectedPlayer || !this.selectedParticipantId || this.amount < this.minimumAmount) return;
        this.dialogRef.close({
            playerId: this.selectedPlayer.id,
            playerName: this.selectedPlayer.name,
            playerTeam: this.selectedPlayer.team,
            participantId: this.selectedParticipantId,
            amount: this.amount
        });
    }

    ngOnDestroy() {
        this.playerSearch?.unsubscribe();
        this.participantSearch?.unsubscribe();
    }

    cancel() {
        this.dialogRef.close();
    }
}
