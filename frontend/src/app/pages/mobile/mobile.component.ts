import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { trigger, transition, style, animate } from '@angular/animations';

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
    player = '';
    team = '';
    round: any = null;
    amount: number | null = null;
    counts: any = {};
    status = '';
    activeUsers: string[] = [];

    private socket: WebSocket | null = null;

    constructor(private route: ActivatedRoute, private api: ApiService) {}

    ngOnInit() {
        this.pid = Number(this.route.snapshot.queryParamMap.get('pid'));
        if (this.pid) {
            this.loadParticipant();
            this.loadRound();
            this.connectWebSocket();
        } else {
            this.status = 'Manca ?pid=ID nell’URL';
        }
    }

    ngOnDestroy() {
        this.socket?.close();
    }

    loadParticipant() {
        if (!this.pid) return;
        this.api.getParticipant(this.pid).subscribe({
            next: res => { this.participant = res; },
            error: () => { this.status = 'Errore nel caricamento partecipante'; }
        });
    }

    loadRound() {
        this.api.getRound().subscribe({
            next: (res: any) => { this.round = res || null; },
            error: () => { this.round = null; }
        });
    }

    connectWebSocket() {
        this.socket = this.api.connectWebSocket();

        this.socket.onopen = () => console.log('WS mobile connesso');
        this.socket.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);

                if (msg?.type === 'ROUND_STARTED') {
                    this.loadRound();
                    this.loadParticipant();
                    this.activeUsers = []; // 🔹 reset lista quando parte nuovo round
                }

                if (msg?.type === 'ROUND_CLOSED') {
                    this.loadRound();
                    this.loadParticipant();
                    this.activeUsers = []; // 🔹 svuota lista
                }

                if (msg?.type === 'ROUND_RESET') {
                    this.round = null;
                    this.activeUsers = []; // 🔹 reset totale
                }

                if (msg?.type === 'BID_ADDED') {
                    const user = msg.payload?.user;
                    if (user && !this.activeUsers.includes(user)) {
                        this.activeUsers.push(user);
                    }
                }
            } catch {}
        };
        this.socket.onclose = () => setTimeout(() => this.connectWebSocket(), 1500);
    }

    send() {
        if (!this.participant || !this.pid || !this.amount) return;
        this.api.sendBid(this.pid, this.amount).subscribe({
            next: () => {
                this.status = `Offerta di ${this.amount} inviata`;
                this.amount = null;
                this.loadParticipant();
            },
            error: (err) => this.status = (err?.error?.message || 'Errore nell’invio offerta')
        });
    }
}
