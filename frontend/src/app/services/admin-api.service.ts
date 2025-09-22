import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {Round} from "../models/round.model";

@Injectable({
  providedIn: 'root'
})
export class AdminApiService {
  private base: string;

  constructor(private http: HttpClient) {
    this.base = (window as any).__API_BASE__ || '';
  }

  // 🔹 ROUND CONTROL
  getRound(): Observable<any> {
    return this.http.get(`${this.base}/api/round`);
  }

  startRound(round: Round): Observable<any> {
    return this.http.post(`${this.base}/api/start`, round);
  }

  closeRound(): Observable<any> {
    return this.http.post(`${this.base}/api/round/close`, {});
  }

  resetRound(): Observable<any> {
    return this.http.post(`${this.base}/api/round/reset`, {});
  }

  // 🔹 RANDOM CONTROL
  setRole(role: string): Observable<any> {
    return this.http.post(`${this.base}/api/random/set-role`, { role });
  }

  setRandom(mode: string, role?: string): Observable<any> {
    return this.http.post(`${this.base}/api/random/mode`, { mode, role });
  }

  randomNext(): Observable<any> {
    return this.http.post(`${this.base}/api/random/next`, {});
  }

  randomPrev(current?: { name?: string; team?: string }): Observable<any> {
    return this.http.post(`${this.base}/api/random/prev`, current || {}, { observe: 'response' });
  }

  randomSkip(name: string, team: string): Observable<any> {
    return this.http.post(`${this.base}/api/random/skip`, { name, team });
  }

  randomResetSkip(): Observable<any> {
    return this.http.post(`${this.base}/api/random/reset-skip`, {});
  }

  getRandomState(): Observable<any> {
    return this.http.get(`${this.base}/api/random/state`);
  }

  // 🔹 UPLOAD
  uploadRosterExcel(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.base}/api/admin/rosters/upload`, formData);
  }

  uploadPlayersExcel(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.base}/api/admin/players/upload`, formData);
  }

  // 🔹 MANUAL ASSIGN
  manualAssign(payload: any): Observable<any> {
    return this.http.post(`${this.base}/api/assign`, payload);
  }

  // 🔹 WEBSOCKET
  connectWebSocket(): WebSocket {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return new WebSocket(`${protocol}://${location.host}/ws/round`);
  }
}
