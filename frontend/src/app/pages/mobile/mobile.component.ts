import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { trigger, transition, style, animate } from '@angular/animations';
import { Subscription } from 'rxjs';

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

    // ruolo corrente (sincronizzato con Admin / round attivo)
    currentRole: RoleKey | '' = '';

    // WS & subscriptions
    private socket: WebSocket | null = null;
    private roleSub?: Subscription;

    // UX: mostra l’ultima offerta inviata da questo partecipante
    lastBidAmount: number | null = null;

    constructor(private route: ActivatedRoute, private api: ApiService) {}

    ngOnInit(): void {
        this.pid = Number(this.route.snapshot.queryParamMap.get('pid'));
        if (this.pid) {
            this.loadParticipant();
            this.loadRound();
            this.connectWebSocket();
        } else {
            this.status = 'Manca ?pid=ID nell’URL';
        }

        // sincronizza il filtro ruolo condiviso (ROLE_CHANGED / ROUND_STARTED)
        this.roleSub = this.api.roleFilter$.subscribe(role => {
            this.currentRole = role;
        });
    }

    ngOnDestroy(): void {
        this.roleSub?.unsubscribe();
        this.socket?.close();
    }

    // ---------- API calls ----------
    loadParticipant() {
        if (!this.pid) return;
        this.api.getParticipant(this.pid).subscribe({
            next: res => { this.participant = res; },
            error: () => { this.status = 'Errore nel caricamento partecipante'; }
        });
    }

    loadRound() {
        this.api.getRound().subscribe({
            next: (res: any) => {
                this.round = res || null;
                // se round nuovo è partito, resetta l’ultima offerta mostrata
                if (this.round && this.round.closed === false) {
                    this.lastBidAmount = null;
                }
            },
            error: () => { this.round = null; }
        });
    }

    // ---------- WebSocket ----------
    connectWebSocket() {
        this.socket = this.api.connectWebSocket();

        this.socket.onopen = () => console.log('WS mobile connesso');
        this.socket.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                const type = msg?.type;
                const payload = msg?.payload || msg || {};

                if (type === 'ROUND_STARTED' || type === 'ROUND_UPDATED') {
                    this.loadRound();
                    this.activeUsers = [];           // reset lista attivi a inizio/aggiornamento
                    this.lastBidAmount = null;       // pulisci l’ultima offerta a inizio round
                }

                if (type === 'ROUND_CLOSED') {
                    this.loadRound();
                    this.loadParticipant();          // aggiorna crediti/roster
                    this.activeUsers = [];           // svuota lista attivi
                }

                if (type === 'ROUND_RESET') {
                    this.round = null;
                    this.activeUsers = [];
                    this.lastBidAmount = null;
                }

                if (type === 'BID_ADDED') {
                    const user = payload?.user;
                    const participantId = payload?.participantId;
                    const amount = payload?.amount;

                    if (user && !this.activeUsers.includes(user)) {
                        this.activeUsers.push(user);
                    }
                    // se è la mia offerta, aggiorna il riepilogo locale
                    if (this.pid && participantId === this.pid && typeof amount === 'number') {
                        this.lastBidAmount = amount;
                    }
                }
            } catch { /* ignore parse errors */ }
        };

        // semplice autoreconnect
        this.socket.onclose = () => setTimeout(() => this.connectWebSocket(), 1500);
    }

    // ---------- Azioni ----------
    isBidAllowed(): boolean {
        // se c’è lista ammessi (spareggio), consenti solo se pid è incluso
        const allowed = this.round?.allowedUsers;
        if (Array.isArray(allowed) && allowed.length > 0 && this.pid) {
            return allowed.includes(this.pid);
        }
        return true; // round normale: tutti ammessi
    }

    send() {
        if (!this.participant || !this.pid || this.amount == null) return;

        // blocco client-side in caso di spareggio e non ammesso
        if (!this.isBidAllowed()) {
            this.status = 'Spareggio in corso: non sei tra gli ammessi a rilanciare';
            return;
        }

        const v = Number(this.amount);
        if (!Number.isFinite(v) || v <= 0) {
            this.status = 'Inserisci un importo valido';
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
