import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class ApiService {

    private base: string;

    constructor(private http: HttpClient) {
        // Legge l'API base da index.html oppure fallback
        this.base = (window as any).__API_BASE__ || '';
    }

    randomPrev() {
        return this.http.post<any>(`${this.base}/api/random/prev`, {});
    }

    setRole(role: string): Observable<any> {
        return this.http.post(`${this.base}/api/random/set-role`, { role });
    }

    // 🔹 ROUND
    getRound(): Observable<any> {
        return this.http.get(`${this.base}/api/round`);
    }

    startRound(pin: string, round: { player: string; team?: string; role: string; duration: number; tieBreak?: string }) {
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
        return new WebSocket(`${protocol}://${location.host}/ws/round`);
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



}
