import {Component, OnInit} from '@angular/core';
import {ApiService} from "../../services/api.service";
import { MatDialog } from '@angular/material/dialog';
import { ManualAssignDialogComponent } from '../../dialogs/manual-assign-dialog.component'; // 🔹 nuovo file


type SummaryRow = { name: string, remainingCredits: number, need: any };

@Component({
    selector: 'app-admin',
    templateUrl: './admin.component.html',
    styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {
    pin = '1234';
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
// --- Sidenav Summary (destra) ---
    summaryOpen = true;  // di default aperta
    winnerPayload: { user: string; amount: number; player: string } | null = null;


    toggleSummary(open?: boolean) {
        this.summaryOpen = (open !== undefined) ? open : !this.summaryOpen;
    }


    constructor(private api: ApiService, private dialog: MatDialog) {
    }

    ngOnInit() {
        this.connectWs();
        this.load();
        this.refreshRemaining();
    }

    ngOnDestroy() {
        if (this.timerInterval) {
            clearInterval(this.timerInterval)
        }
    }

    // admin.component.ts
    prev() {
        const current = (this.player && this.team !== undefined)
            ? { name: this.player, team: this.team }
            : {};

        this.api.randomPrev(current).subscribe({
            next: (res) => {
                if (res.status === 204 || !res.body) {
                    // nessuna “precedente” trovata
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
        const sock = this.api.connectWebSocket();
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
                // payload coerente: RoundDto
                const payload = data.payload || null;

                // Stoppa timer e pulisci lista attivi
                if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
                this.timeLeft = null;
                this.activeUsers = [];

                // Mostra overlay SOLO se il payload ha un winner
                if (payload?.winner) {
                    // Salva un payload minimale per l'overlay (così non dipende da this.round)
                    this.winnerPayload = {
                        user: payload.winner.user,
                        amount: payload.winner.amount,
                        player: payload.player
                    };
                    this.showWinnerOverlay = true;
                    setTimeout(() => { this.showWinnerOverlay = false; this.winnerPayload = null; }, 5000);
                } else {
                    // niente winner → nessun overlay (parità)
                    this.showWinnerOverlay = false;
                    this.winnerPayload = null;
                }

                // Allinea stato round dall’API (asincrono, non influenza overlay)
                this.load();
                this.refreshRemaining();

                // Se c’è un winner → avanza SUBITO al prossimo giocatore (senza avviare round)
                if (payload?.winner) {
                    // evita doppi next sullo stesso round
                    const closedId = payload.roundId || null;
                    if (!closedId || this.lastHandledClosedRoundId !== closedId) {
                        this.lastHandledClosedRoundId = closedId;
                        this.api.randomNext().subscribe(d => {
                            if (d) {
                                this.player = d.name || '';
                                this.team = d.team || '';
                                this.prole = d.role || '';
                                this.value = d.value || 0;
                            } else {
                                // fine giro
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
        this.api.closeRound().subscribe((res) => {
            this.round = res;
            this.refreshRemaining();
            this.activeUsers = [];

            // stop timer locale
            if (this.timerInterval) {
                clearInterval(this.timerInterval);
                this.timerInterval = null;
            }
            this.timeLeft = null;

            // Non chiamiamo next() qui: lo farà il ramo ROUND_CLOSED del WebSocket.
            // L'overlay lo gestiamo anche via WS per avere un solo flusso uniforme.
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
        this.api.getRound().subscribe(res => {
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
        this.api.getRandomState().subscribe(res => {
            const role = this.role || 'TUTTI';
            this.remainingCount = res.remaining?.[role] ?? 0;
            this.skippedCount = res.skipped?.[role] ?? 0;
        });
    }

    // ---- Azioni round ----
    next() {
        this.api.randomNext().subscribe(d => {
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
        this.api.randomSkip(this.player, this.team).subscribe(() => {
            this.next();
            this.refreshRemaining();
        });
    }

    resetSkip() {
        this.api.randomResetSkip().subscribe(() => {
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
        if (!this.pin || !this.pin.trim()) {
            alert('PIN mancante');
            return;
        }

        const payload: any = {
            player: this.player,
            playerTeam: this.team || '',
            playerRole: this.prole,
            value: this.value,
            durationSeconds: Number(this.duration),
            tieBreak: 'NONE'
        };
        if (this.round?.closed && Array.isArray(this.round.tieUserIds) && this.round.tieUserIds.length) {
            payload.allowedUsers = this.round.tieUserIds;   // ⬅️ passa gli id al BE
        }
        this.api.startRound(this.pin, payload).subscribe({
            next: () => {
                this.load();
                this.refreshRemaining();
                this.showWinnerOverlay = false;
            },
            error: (err) => alert('Errore avvio round: ' + (err?.error?.message || err?.message || 'sconosciuto'))
        });
    }

    startAuction() {
        this.api.randomNext().subscribe(d => {
            if (d) {
                this.player = d.name || '';
                this.team = d.team || '';
                this.prole = d.role || '';
                this.value = d.value || 0;
            }
            this.refreshRemaining();
        });
    }

    closeAuction() {
        // TODO: chiamata API che chiude l’intera sessione/mercato
        // es: this.api.closeAuction().subscribe(...)
        console.log("Chiusura asta (da implementare lato BE con salvataggio roster_history)");
    }

    reset() {
        this.api.resetRound(this.pin).subscribe(() => {
            this.load();
            this.refreshRemaining();
            this.showWinnerOverlay = false;
        });
    }

    changeRole() {
        if (!this.role) return;
        this.api.setRole(this.role).subscribe({
            next: () => this.refreshRemaining(),
            error: (err) => console.error("Errore setRole", err)
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
                this.api.manualAssign({
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


}
