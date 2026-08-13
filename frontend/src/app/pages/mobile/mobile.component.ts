import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { Subscription } from 'rxjs';
import {UserApiService} from "../../services/user-api.service";

type RoleKey = 'PORTIERE' | 'DIFENSORE' | 'CENTROCAMPISTA' | 'ATTACCANTE';

@Component({
    selector: 'app-mobile',
    templateUrl: './mobile.component.html',
    styleUrls: ['./mobile.component.css'],
    animations: [
        trigger('fadeInOut', [
            transition(':enter', [
                style({ opacity: 0, transform: 'translateY(5px)' }),
                animate('400ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
            ]),
            transition(':leave', [
                animate('300ms ease-in', style({ opacity: 0, transform: 'translateY(-5px)' }))
            ])
        ])
    ]
})
export class MobileComponent implements OnInit, OnDestroy {
    pid: number | null = null;
    participant: any = null;

    // round & stato UI
    round: any = null;
    amount: number | null = null;
    status = '';
    activeUsers: string[] = [];
    timeLeft: number | null = null;
    remainingCalls = 0;
    openSlots = 0;
    private timerInterval?: ReturnType<typeof setInterval>;

    // ruolo corrente (sincronizzato con Admin / round attivo)
    currentRole: RoleKey | '' = '';

    // WS & subscriptions
   // private socket: WebSocket | null = null;
    private roleSub?: Subscription;
    private roundSub?: Subscription;
    private activeUsersSub?: Subscription;
    private summarySub?: Subscription;

    // UX: mostra l’ultima offerta inviata da questo partecipante
    lastBidAmount: number | null = null;

    constructor(private route: ActivatedRoute, private api: UserApiService) {}

    ngOnInit(): void {
        this.pid = Number(this.route.snapshot.queryParamMap.get('pid'));
        if (this.pid) {
            this.loadParticipant();
            this.loadRound();
        } else {
            this.loadCurrentParticipant();
            this.loadRound();
        }

        // sincronizza il filtro ruolo condiviso (ROLE_CHANGED / ROUND_STARTED)
        this.roleSub = this.api.roleFilter$.subscribe(role => {
            this.currentRole = role;
            this.loadMarketStats();
        });

        this.roundSub = this.api.round$.subscribe(round => {
            this.round = round;
            this.configureTimer();
            this.loadMarketStats();
            if (!round) {
                this.activeUsers = [];
                this.lastBidAmount = null;
            }
        });

        this.activeUsersSub = this.api.activeUsers$.subscribe(users => {
            this.activeUsers = users;
        });
        this.summarySub = this.api.summaryUpdated$.subscribe(() => {
            if (this.pid) this.loadParticipant();
            this.loadMarketStats();
        });
    }

    ngOnDestroy(): void {
        this.roleSub?.unsubscribe();
        this.roundSub?.unsubscribe();
        this.activeUsersSub?.unsubscribe();
        this.summarySub?.unsubscribe();
        if (this.timerInterval) clearInterval(this.timerInterval);
      //  this.socket?.close();
    }

    // ---------- API calls ----------
    loadParticipant() {
        if (!this.pid) return;
        this.api.getParticipant(this.pid).subscribe({
            next: res => { this.participant = res; },
            error: () => { this.status = 'Errore nel caricamento partecipante'; }
        });
    }

    loadCurrentParticipant() {
        this.api.getCurrentParticipant().subscribe({
            next: res => {
                this.participant = res;
                this.pid = res?.id || null;
            },
            error: () => { this.status = 'Partecipante non trovato per l’utente corrente'; }
        });
    }

    loadRound() {
        this.api.getRound().subscribe({
            next: (res: any) => {
                this.round = res || null;
                this.configureTimer();
                this.activeUsers = Object.keys(this.round?.bids || {});
                // se round nuovo è partito, resetta l’ultima offerta mostrata
                if (this.round && this.round.closed === false) {
                    this.lastBidAmount = null;
                }
            },
            error: () => { this.round = null; }
        });
    }

    loadMarketStats(): void {
        const role = this.activeRole;
        if (!role) return;
        this.api.getRandomState().subscribe({
            next: state => {
                this.remainingCalls = Number(state?.remaining?.[role] || 0);
                this.openSlots = Number(state?.openSlots?.[role] || 0);
            },
            error: () => undefined
        });
    }

    get activeRole(): RoleKey | '' {
        return (this.round && !this.round.closed ? this.round.playerRole : this.currentRole) || '';
    }

    // ---------- WebSocket ----------


    // ---------- Azioni ----------
    isBidAllowed(): boolean {
        // se c’è lista ammessi (spareggio), consenti solo se pid è incluso
        const allowed = this.round?.allowedUsers;
        if (!this.pid || !this.round || this.round.closed || this.timeLeft === 0) return false;
        if (Array.isArray(allowed) && allowed.length > 0) {
            return allowed.map((id: unknown) => Number(id)).includes(Number(this.pid));
        }
        return true; // round normale: tutti ammessi
    }

    get sortedBids(): { user: string; amount: number }[] {
        return Object.entries(this.round?.bids || {})
            .map(([user, amount]) => ({ user, amount: Number(amount) }))
            .sort((a, b) => b.amount - a.amount || a.user.localeCompare(b.user));
    }

    get automaticMinimumMessage(): string | null {
        const bids = this.sortedBids;
        const charged = Number(this.round?.winner?.amount);
        if (!this.round?.closed || !this.round?.winner || bids.length !== 1 || charged >= bids[0].amount) return null;
        return `Unico offerente: costo assegnato d’ufficio a ${charged} ${charged === 1 ? 'credito' : 'crediti'}.`;
    }

    isMyBid(user: string): boolean {
        return !!this.participant?.name && user === this.participant.name;
    }

    isWinner(): boolean {
        return !!this.pid && Number(this.round?.winner?.participantId) === Number(this.pid);
    }

    get maxBidForCurrentRound(): number {
        const singlePlayerMax = Number(this.participant?.maxBid ?? this.participant?.remainingCredits ?? 0);
        const purchaseSize = Math.max(1, Number(this.round?.purchaseSize || 1));
        return singlePlayerMax + purchaseSize - 1;
    }

    private configureTimer(): void {
        if (this.timerInterval) clearInterval(this.timerInterval);
        this.updateTimeLeft();
        if (this.round && !this.round.closed && this.round.endEpochMillis) {
            this.timerInterval = setInterval(() => this.updateTimeLeft(), 250);
        }
    }

    private updateTimeLeft(): void {
        if (!this.round || this.round.closed || !this.round.endEpochMillis) {
            this.timeLeft = null;
            return;
        }
        this.timeLeft = Math.max(0, Math.ceil((Number(this.round.endEpochMillis) - Date.now()) / 1000));
        if (this.timeLeft === 0 && this.timerInterval) {
            clearInterval(this.timerInterval);
            this.timerInterval = undefined;
            this.api.refreshRound();
        }
    }

    send() {
        if (!this.participant || !this.pid || this.amount == null) return;

        // blocco client-side in caso di spareggio e non ammesso
        if (!this.isBidAllowed()) {
            this.status = 'Spareggio in corso: non sei tra gli ammessi a rilanciare';
            return;
        }

        const v = Number(this.amount);
        const minimumBid = Number(this.round?.minimumBid || 1);
        if (!Number.isFinite(v) || v < minimumBid) {
            this.status = `Offerta minima ${minimumBid}`;
            return;
        }

        if (v <= 0) {
            this.status = 'Inserisci un importo valido';
            return;
        }
        if (v > this.maxBidForCurrentRound) {
            this.status = `Offerta massima ${this.maxBidForCurrentRound}`;
            return;
        }

        this.api.sendBid(this.pid, v).subscribe({
            next: () => {
                this.lastBidAmount = v;            // feedback immediato
                this.status = `Offerta di ${v} inviata`;
                this.amount = null;                // pulisci input
                this.loadParticipant();            // aggiorna crediti
            },
            error: (err) => {
                this.status = (err?.error?.message || 'Errore nell’invio offerta');
            }
        });
    }
}
