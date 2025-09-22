import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, BehaviorSubject } from 'rxjs';

type RoleKey = 'PORTIERE' | 'DIFENSORE' | 'CENTROCAMPISTA' | 'ATTACCANTE';

@Injectable({
  providedIn: 'root'
})
export class UserApiService {

  private base: string;
  public summaryUpdated$ = new Subject<void>();
  public roleFilter$ = new BehaviorSubject<RoleKey | ''>('');

  constructor(private http: HttpClient) {
    this.base = (window as any).__API_BASE__ || '';
    this.connectWebSocket();
  }

  // 🔹 ROSTERS
  getRosters(participant?: string): Observable<any> {
    let params = new HttpParams();
    if (participant) params = params.set('participant', participant);
    return this.http.get<any>(`${this.base}/api/rosters`, { params });
  }

  // 🔹 ROUND (solo consultazione)
  getRound(): Observable<any> {
    return this.http.get(`${this.base}/api/round`);
  }

  // 🔹 RANDOM (solo consultazione)
  getRandomState(): Observable<any> {
    return this.http.get(`${this.base}/api/random/state`);
  }

  // 🔹 PLAYERS
  getPlayers(params?: { role?: string }): Observable<any[]> {
    let httpParams = new HttpParams();
    if (params?.role) httpParams = httpParams.set('role', params.role);
    return this.http.get<any[]>(`${this.base}/api/players/free`, { params: httpParams });
  }

  // 🔹 PARTICIPANTS
  getParticipantsSummary(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/participant/summary`);
  }

  getParticipant(id: number): Observable<any> {
    return this.http.get(`${this.base}/api/participant/${id}`);
  }

  getAllParticipants(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/participant/all`);
  }

  getParticipants(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/participant/all`);
  }

  getSummary(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/participant/summary`);
  }

  // 🔹 BIDS
  sendBid(participantId: number, amount: number): Observable<any> {
    return this.http.post(`${this.base}/api/bids`, { participantId, amount });
  }

  // 🔹 WEBSOCKET
  connectWebSocket(): WebSocket {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${protocol}://${location.host}/ws/round`);

    ws.addEventListener('message', (evt) => {
      try {
        const data = JSON.parse((evt as MessageEvent).data);
        const t = data?.type;
        const payload = data?.payload || data || {};

        if (t === 'SUMMARY_UPDATED' || t === 'ROUND_CLOSED' || t === 'ROUND_RESET') {
          this.summaryUpdated$.next();
        }

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
}
