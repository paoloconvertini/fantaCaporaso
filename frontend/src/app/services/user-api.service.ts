import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, BehaviorSubject } from 'rxjs';

type RoleKey = 'PORTIERE' | 'DIFENSORE' | 'CENTROCAMPISTA' | 'ATTACCANTE';

@Injectable({
  providedIn: 'root'
})
export class UserApiService {

  private base: string;
  public round$ = new BehaviorSubject<any | null>(null);
  public summaryUpdated$ = new Subject<void>();
  public roleFilter$ = new BehaviorSubject<RoleKey | ''>('');
  public activeUsers$ = new BehaviorSubject<string[]>([]);
  private socket?: WebSocket;
  private reconnectTimer?: any;

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

  /** Recupera lo stato canonico dopo navigazioni o messaggi WebSocket persi. */
  refreshRound(): void {
    this.getRound().subscribe({
      next: round => this.applyRound(round),
      // Un errore di rete temporaneo non deve cancellare un round gia' visibile.
      error: () => undefined
    });
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

  getCurrentParticipant(): Observable<any> {
    return this.http.get(`${this.base}/api/participant/me`);
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
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
      return this.socket;
    }

    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${protocol}://${location.host}/ws/round`);
    this.socket = ws;

      ws.addEventListener('message', (evt) => {
          try {
              const data = JSON.parse((evt as MessageEvent).data);
              const t = data?.type;
              const payload = data?.payload || data || {};

              if (t === 'SUMMARY_UPDATED' || t === 'ROUND_CLOSED' || t === 'ROUND_RESET') {
                  this.summaryUpdated$.next();
              }

              if (t === 'BID_ADDED' && payload?.user) {
                  const users = this.activeUsers$.value;
                  if (!users.includes(payload.user)) {
                      this.activeUsers$.next([...users, payload.user]);
                  }
              }

              if (t === 'ROLE_CHANGED' && payload?.role) {
                  this.roleFilter$.next(payload.role as RoleKey);
              }

              if (t === 'ROUND_STARTED') {
                  this.applyRound(payload);
                  // Il payload rende immediata la UI; l'API la riallinea allo stato persistito.
                  this.refreshRound();
              }

              if (t === 'ROUND_CLOSED') {
                  this.activeUsers$.next([]);
                  this.applyRound(payload);
              }

              if (t === 'ROUND_RESET') {
                  this.activeUsers$.next([]);
                  this.round$.next(null);
              }
          } catch { /* ignore JSON errors */ }
      });

      ws.addEventListener('open', () => {
          // Se ROUND_STARTED e' arrivato mentre il telefono era offline, lo recuperiamo qui.
          this.refreshRound();
      });

      ws.addEventListener('close', () => {
          this.socket = undefined;
          clearTimeout(this.reconnectTimer);
          this.reconnectTimer = setTimeout(() => this.connectWebSocket(), 2000);
      });

      ws.addEventListener('error', () => {
          ws.close();
      });

      return ws;
  }

  private applyRound(round: any | null): void {
    this.round$.next(round || null);
    if (!round) {
      this.activeUsers$.next([]);
      return;
    }
    if (round.playerRole) {
      this.roleFilter$.next(round.playerRole as RoleKey);
    }
    this.activeUsers$.next(round.closed
      ? Object.keys(round.bids || {})
      : [...(round.bidders || [])]);
  }
}
