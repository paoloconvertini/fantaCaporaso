import { Component, OnInit } from '@angular/core';
import { ApiService } from "../../services/api.service";

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
    duration = 60;
    activeUsers: string[] = [];
    remaining: any = {};
    round: any = null;
    showWinnerOverlay = false;
    skippedCount = 0;
    remainingCount = 0;

    constructor(private api: ApiService) {}

    ngOnInit() {
        this.connectWs();
        this.load();
        this.refreshRemaining();
    }

    prev() {
        this.api.randomPrev().subscribe(d => {
            if (!d) {
                this.player = '(inizio giro)';
                this.team = '';
                this.prole = '';
            } else {
                this.player = d.name || '';
                this.team = d.team || '';
                this.prole = d.role || '';
            }
            this.refreshRemaining();
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
                this.load();
                this.refreshRemaining();
                this.activeUsers = []; // reset lista attivi
            }

            if (data.type === 'ROUND_RESET') {
                this.round = null;
                this.activeUsers = [];
            }
        };
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
        });
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
            } else {
                this.player = d.name || '';
                this.team = d.team || '';
                this.prole = d.role || '';
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

        const payload = {
            player: this.player,
            team: this.team || '',
            role: this.prole,
            duration: Number(this.duration),
            tieBreak: 'NONE'
        };

        this.api.startRound(this.pin, payload).subscribe({
            next: () => {
                this.load();
                this.refreshRemaining();
                this.showWinnerOverlay = false;
            },
            error: (err) => alert('Errore avvio round: ' + (err?.error?.message || err?.message || 'sconosciuto'))
        });
    }

    close() {
        this.api.closeRound().subscribe(() => {
            this.load();
            this.refreshRemaining();
            this.showWinnerOverlay = true;
            this.activeUsers = [];
        });
        setTimeout(() => {
            this.showWinnerOverlay = false;
        }, 5000);
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

}
