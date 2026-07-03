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

    // ruolo corrente (sincronizzato con Admin / round attivo)
    currentRole: RoleKey | '' = '';

    // WS & subscriptions
   // private socket: WebSocket | null = null;
    private roleSub?: Subscription;
    private roundSub?: Subscription;
    private activeUsersSub?: Subscription;

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
        });

        this.roundSub = this.api.round$.subscribe(round => {
            this.round = round;
            if (!round) {
                this.activeUsers = [];
                this.lastBidAmount = null;
            }
        });

        this.activeUsersSub = this.api.activeUsers$.subscribe(users => {
            this.activeUsers = users;
        });
    }

    ngOnDestroy(): void {
        this.roleSub?.unsubscribe();
        this.roundSub?.unsubscribe();
        this.activeUsersSub?.unsubscribe();
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
                this.activeUsers = Object.keys(this.round?.bids || {});
                // se round nuovo è partito, resetta l’ultima offerta mostrata
                if (this.round && this.round.closed === false) {
                    this.lastBidAmount = null;
                }
            },
            error: () => { this.round = null; }
        });
    }

    // ---------- WebSocket ----------


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
        const minimumBid = Number(this.round?.minimumBid || 1);
        if (!Number.isFinite(v) || v < minimumBid) {
            this.status = `Offerta minima ${minimumBid}`;
            return;
        }

        if (v <= 0) {
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
