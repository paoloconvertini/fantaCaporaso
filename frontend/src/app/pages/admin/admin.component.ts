import { Component, OnInit, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { AdminApiService } from '../../services/admin-api.service';
import { ManualAssignDialogComponent } from '../../dialogs/manual-assign-dialog.component';
import {Round} from "../../models/round.model";

type SummaryRow = { name: string, remainingCredits: number, need: any };

@Component({
    selector: 'app-admin',
    templateUrl: './admin.component.html',
    styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit, OnDestroy {
    role = '';
    player = '';
    team = '';
    prole = '';
    duration = 30;
    timeLeft: number | null = null;
    private timerInterval: any;
    activeUsers: string[] = [];
    remaining: any = {};
    round: any = null;
    showWinnerOverlay = false;
    skippedCount = 0;
    remainingCount = 0;
    value = 0;
    loadingAssign = false;
    private lastHandledClosedRoundId: string | null = null;
    summaryOpen = true;
    winnerPayload: { user: string; amount: number; player: string } | null = null;

    constructor(
        private adminApi: AdminApiService,
        private dialog: MatDialog
    ) {}

    toggleSummary(open?: boolean) {
        this.summaryOpen = (open !== undefined) ? open : !this.summaryOpen;
    }

    ngOnInit() {
        this.connectWs();
        this.load();
        this.refreshRemaining();
    }

    ngOnDestroy() {
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
        }
    }

    // --- ROUND NAVIGATION ---
    prev() {
        const current = (this.player && this.team !== undefined)
            ? { name: this.player, team: this.team }
            : {};

        this.adminApi.randomPrev(current).subscribe({
            next: (res) => {
                if (res.status === 204 || !res.body) {
                    this.player = '(inizio giro)';
                    this.team = '';
                    this.prole = '';
                    this.value = 0;
                } else {
                    const d = res.body;
                    this.player = d.name || '';
                    this.team = d.team || '';
                    this.prole = d.role || '';
                    this.value = d.value || 0;
                }
                this.refreshRemaining();
            },
            error: (err) => {
                console.error('prev() error', err);
                this.player = '(inizio giro)';
                this.team = '';
                this.prole = '';
                this.value = 0;
                this.refreshRemaining();
            }
        });
    }

    connectWs() {
        const sock = this.adminApi.connectWebSocket();
        sock.onmessage = (msg) => {
            const data = JSON.parse(msg.data);

            if (data.type === 'BID_ADDED') {
                if (data.payload?.user && !this.activeUsers.includes(data.payload.user)) {
                    this.activeUsers.push(data.payload.user);
                }
            }

            if (data.type === 'ROUND_UPDATED') {
                this.load();
                this.refreshRemaining();
            }

            if (data.type === 'ROUND_CLOSED') {
                const payload = data.payload || null;

                if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
                this.timeLeft = null;
                this.activeUsers = [];

                if (payload?.winner) {
                    this.winnerPayload = {
                        user: payload.winner.user,
                        amount: payload.winner.amount,
                        player: payload.player
                    };
                    this.showWinnerOverlay = true;
                    setTimeout(() => { this.showWinnerOverlay = false; this.winnerPayload = null; }, 5000);
                } else {
                    this.showWinnerOverlay = false;
                    this.winnerPayload = null;
                }

                this.load();
                this.refreshRemaining();

                if (payload?.winner) {
                    const closedId = payload.roundId || null;
                    if (!closedId || this.lastHandledClosedRoundId !== closedId) {
                        this.lastHandledClosedRoundId = closedId;
                        this.adminApi.randomNext().subscribe(d => {
                            if (d) {
                                this.player = d.name || '';
                                this.team = d.team || '';
                                this.prole = d.role || '';
                                this.value = d.value || 0;
                            } else {
                                this.player = '(fine giro)';
                                this.team = '';
                                this.prole = '';
                                this.value = 0;
                            }
                            this.refreshRemaining();
                        });
                    }
                }
            }

            if (data.type === 'ROUND_RESET') {
                this.round = null;
                this.activeUsers = [];
            }
        };
    }

    close() {
        this.adminApi.closeRound().subscribe((res) => {
            this.round = res;
            this.refreshRemaining();
            this.activeUsers = [];

            if (this.timerInterval) {
                clearInterval(this.timerInterval);
                this.timerInterval = null;
            }
            this.timeLeft = null;
        });
    }

    get sortedBids() {
        if (!this.round || !this.round.bids) return [];
        const arr = Object.entries(this.round.bids).map(([user, amount]) => ({
            user,
            amount: Number(amount)
        }));
        return arr.sort((a, b) => b.amount - a.amount);
    }

    load() {
        this.adminApi.getRound().subscribe(res => {
            this.round = res;
            this.activeUsers = this.round && this.round.bids ? Object.keys(this.round.bids) : [];

            const end = this.round?.endEpochMillis as number | null;
            const isActive = !!this.round && this.round.closed === false && !!end && end > Date.now();

            if (isActive) {
                if (this.timerInterval) clearInterval(this.timerInterval);
                this.updateTimeLeft();
                this.timerInterval = setInterval(() => this.updateTimeLeft(), 1000);
            } else {
                if (this.timerInterval) {
                    clearInterval(this.timerInterval);
                    this.timerInterval = null;
                }
                this.timeLeft = null;
            }
        });
    }

    private updateTimeLeft() {
        if (!this.round?.endEpochMillis) return;
        const diff = Math.max(0, Math.floor((this.round.endEpochMillis - Date.now()) / 1000));
        this.timeLeft = diff;
        if (diff <= 0) {
            clearInterval(this.timerInterval);
            this.timerInterval = null;
            this.timeLeft = 0;
        }
    }

    refreshRemaining() {
        this.adminApi.getRandomState().subscribe(res => {
            const role = this.role || 'TUTTI';
            this.remainingCount = res.remaining?.[role] ?? 0;
            this.skippedCount = res.skipped?.[role] ?? 0;
        });
    }

    next() {
        this.adminApi.randomNext().subscribe(d => {
            if (!d) {
                this.player = '(fine giro)';
                this.team = '';
                this.prole = '';
                this.value = 0;
                return;
            } else {
                this.player = d.name || '';
                this.team = d.team || '';
                this.prole = d.role || '';
                this.value = d.value || 0;
            }
            this.refreshRemaining();
        });
    }

    skip() {
        if (!this.player) return;
        this.adminApi.randomSkip(this.player, this.team).subscribe(() => {
            this.next();
            this.refreshRemaining();
        });
    }

    resetSkip() {
        this.adminApi.randomResetSkip().subscribe(() => {
            this.player = '';
            this.team = '';
            this.prole = '';
            this.refreshRemaining();
        });
    }

    start() {
        if (!this.player || !this.prole) {
            alert('Scegli prima un giocatore');
            return;
        }
        if (!this.duration || this.duration < 5) {
            alert('Durata non valida (min 5s)');
            return;
        }

        const payload: Round = {
            player: this.player,
            playerTeam: this.team || '',
            playerRole: this.prole,
            value: this.value,
            durationSeconds: Number(this.duration),
            tieBreak: 'NONE'
        };
        if (this.round?.closed && Array.isArray(this.round.tieUserIds) && this.round.tieUserIds.length) {
            payload.allowedUsers = this.round.tieUserIds;
        }

        this.adminApi.startRound(payload).subscribe({
            next: () => {
                this.load();
                this.refreshRemaining();
                this.showWinnerOverlay = false;
            },
            error: (err) => alert('Errore avvio round: ' + (err?.error?.message || err?.message || 'sconosciuto'))
        });
    }

    startAuction() {
        this.adminApi.randomNext().subscribe(d => {
            if (d) {
                this.player = d.name || '';
                this.team = d.team || '';
                this.prole = d.role || '';
                this.value = d.value || 0;
            }
            this.refreshRemaining();
        });
    }

    reset() {
        this.adminApi.resetRound().subscribe(() => {
            this.load();
            this.refreshRemaining();
            this.showWinnerOverlay = false;
        });
    }

    changeRole() {
        if (!this.role) return;
        this.adminApi.setRole(this.role).subscribe({
            next: () => this.refreshRemaining(),
            error: (err) => console.error('Errore setRole', err)
        });
    }

    currentRole() {
        return this.round?.playerRole || '';
    }

    needFor(r: SummaryRow) {
        const cr = this.currentRole();
        if (!cr) return 0;
        return r.need?.[cr] ?? 0;
    }

    decreaseDuration() {
        this.duration = Math.max(5, this.duration - 5);
    }

    increaseDuration() {
        this.duration = this.duration + 5;
    }

    openManualAssign() {
        const dialogRef = this.dialog.open(ManualAssignDialogComponent, {
            width: '400px',
            data: { player: this.player, team: this.team, role: this.prole, value: this.value }
        });

        dialogRef.afterClosed().subscribe(result => {
            if (result) {
                this.loadingAssign = true;
                this.adminApi.manualAssign({
                    participantId: result.participantId,
                    player: this.player,
                    team: this.team,
                    role: this.prole,
                    value: this.value,
                    amount: result.amount
                }).subscribe(() => {
                    this.load();
                    this.refreshRemaining();
                    this.loadingAssign = false;
                });
            }
        });
    }

    closeAuction() {
        //TODO creare il metodo nel BE
    }
}
