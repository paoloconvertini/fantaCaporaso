import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, BehaviorSubject } from 'rxjs';

type RoleKey = 'PORTIERE' | 'DIFENSORE' | 'CENTROCAMPISTA' | 'ATTACCANTE';

@Injectable({
    providedIn: 'root'
})
export class ApiService {

    private base: string;
    public summaryUpdated$ = new Subject<void>();

    public roleFilter$ = new BehaviorSubject<RoleKey | ''>('');

    constructor(private http: HttpClient) {
        this.base = (window as any).__API_BASE__ || '';
        this.connectWebSocket();
    }

    notifySummaryUpdated() {
        this.summaryUpdated$.next();
    }

    // api.service.ts
    randomPrev(current?: { name?: string; team?: string }) {
        return this.http.post<any>(
            `${this.base}/api/random/prev`,
            current || {},
            { observe: 'response' }
        );
    }



    setRole(role: string): Observable<any> {
        return this.http.post(`${this.base}/api/random/set-role`, { role });
    }

    // 🔹 ROUND
    getRound(): Observable<any> {
        return this.http.get(`${this.base}/api/round`);
    }

    startRound(pin: string, round: { player: string; playerTeam?: string; playerRole: string;
        durationSeconds: number; tieBreak?: string, allowedUsers?: number[]; }) {
        return this.http.post(`${this.base}/api/start`, round, {
            headers: { 'X-ADMIN-PIN': pin }
        });
    }


    closeRound(): Observable<any> {
        return this.http.post(`${this.base}/api/round/close`, {}, {
            headers: { 'X-ADMIN-PIN': this.adminPin() }
        });
    }

    resetRound(pin: string): Observable<any> {
        return this.http.post(`${this.base}/api/round/reset`, {}, {
            headers: { 'X-ADMIN-PIN': pin }
        });
    }

// 🔹 RANDOM
    getRandomState(): Observable<any> {
        return this.http.get(`${this.base}/api/random/state`);
    }

    setRandom(mode: string, role?: string): Observable<any> {
        return this.http.post(`${this.base}/api/random/mode`, { mode, role }, {
            headers: { 'X-ADMIN-PIN': this.adminPin() }
        });
    }

    randomNext(): Observable<any> {
        return this.http.post(`${this.base}/api/random/next`, {}, {
            headers: { 'X-ADMIN-PIN': this.adminPin() }
        });
    }

    randomSkip(name: string, team: string): Observable<any> {
        return this.http.post(`${this.base}/api/random/skip`, { name, team }, {
            headers: { 'X-ADMIN-PIN': this.adminPin() }
        });
    }

    randomResetSkip(): Observable<any> {
        return this.http.post(`${this.base}/api/random/reset-skip`, {}, {
            headers: { 'X-ADMIN-PIN': this.adminPin() }
        });
    }

// 🔹 PARTECIPANTI
    getParticipantsSummary(): Observable<any[]> {
        return this.http.get<any[]>(`${this.base}/api/participant/summary`);
    }

// 🔹 WEBSOCKET
    connectWebSocket(): WebSocket {
        const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
        const ws = new WebSocket(`${protocol}://${location.host}/ws/round`);

        // Non sovrascrive altri handler: aggiunge e basta
        ws.addEventListener('message', (evt) => {
            try {
                const data = JSON.parse((evt as MessageEvent).data);
                const t = data?.type;
                const payload = data?.payload || data || {};

                // 🔔 Summary da ricaricare
                if (t === 'SUMMARY_UPDATED' || t === 'ROUND_CLOSED' || t === 'ROUND_RESET') {
                    this.summaryUpdated$.next();
                }

                // 🔔 Ruolo aggiornato:
                // - quando l'Admin cambia selezione (ROLE_CHANGED)
                // - quando parte un round (ROUND_STARTED) con playerRole
                if (t === 'ROLE_CHANGED' && payload?.role) {
                    this.roleFilter$.next(payload.role as RoleKey);
                }
                if (t === 'ROUND_STARTED' && payload?.playerRole) {
                    this.roleFilter$.next(payload.playerRole as RoleKey);
                }
            } catch { /* ignore JSON errors */ }
        });

        return ws;
    }

// 🔹 Helper (pin admin) — opzionale
    private adminPin(): string {
        return '1234'; // 🔑 se vuoi centralizzare il pin admin
    }


    // 🔹 Partecipanti
    getParticipant(id: number): Observable<any> {
        return this.http.get(`${this.base}/api/participant/${id}`);
    }

    getAllParticipants(): Observable<any[]> {
        return this.http.get<any[]>(`${this.base}/api/participant/all`);
    }

    // 🔹 Bids
    sendBid(participantId: number, amount: number): Observable<any> {
        return this.http.post(`${this.base}/api/bids`, { participantId, amount });
    }

    // in api.service.ts
    getSummary(): Observable<any[]> {
        return this.http.get<any[]>(`${this.base}/api/participant/summary`);
    }

    manualAssign(payload: any) {
        return this.http.post(`${this.base}/api/assign`, payload);
    }

    getParticipants() {
        return this.http.get<any[]>(`${this.base}/api/participant/all`);
    }

}
