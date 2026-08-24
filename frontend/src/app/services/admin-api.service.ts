import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';
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

  closeAuction(): Observable<any> {
    return this.http.post(`${this.base}/api/admin/close-auction`, {});
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
    return this.http.post(`${this.base}/api/round/skip`, { name, team });
  }

  randomResetSkip(): Observable<any> {
    return this.http.post(`${this.base}/api/random/reset-skip`, {});
  }

  getRandomState(): Observable<any> {
    return this.http.get(`${this.base}/api/random/state`);
  }

  // 🔹 UPLOAD
  uploadRosterExcel(file: File, confirm = false): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('confirm', String(confirm));
    return this.http.post<any>(`${this.base}/api/admin/rosters/upload`, formData);
  }

  exportRostersExcel(): Observable<Blob> {
    return this.http.get(`${this.base}/api/admin/rosters/export`, { responseType: 'blob' });
  }

  uploadPlayersExcel(file: File, confirm = false): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('confirm', String(confirm));
    return this.http.post<any>(`${this.base}/api/admin/players/upload`, formData);
  }

  updateMarketPlayers(file: File, confirm = false): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('confirm', String(confirm));
    return this.http.post<any>(`${this.base}/api/admin/players/market-update`, formData);
  }

  updatePlayerValue(playerId: number, value: number): Observable<void> {
    return this.http.put<void>(`${this.base}/api/admin/players/${playerId}/value`, { value });
  }

  previewNewspaper(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.base}/api/admin/newspaper/preview`, formData);
  }

  getNewspaperPublishStatus(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/admin/newspaper/publish-status`);
  }

  publishNewspaper(edition: any, image: File): Observable<any> {
    const formData = new FormData();
    formData.append('edition', JSON.stringify(edition));
    formData.append('image', image);
    return this.http.post<any>(`${this.base}/api/admin/newspaper/publish`, formData);
  }

  reconcileMarketRosters(file: File, confirm = false): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('confirm', String(confirm));
    return this.http.post<any>(`${this.base}/api/admin/rosters/market-reconcile`, formData);
  }

  getRosterMovements(filters: { q?: string; type?: string; participantId?: number; includeReverted?: boolean }): Observable<any[]> {
    let params = new HttpParams();
    if (filters.q) params = params.set('q', filters.q);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.participantId) params = params.set('participantId', String(filters.participantId));
    if (filters.includeReverted) params = params.set('includeReverted', 'true');
    return this.http.get<any[]>(`${this.base}/api/admin/roster-movements`, { params });
  }

  revertRosterMovement(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/api/admin/roster-movements/${id}/revert`, {});
  }

  // 🔹 MANUAL ASSIGN
  manualAssign(payload: any): Observable<any> {
    return this.http.post(`${this.base}/api/assign`, payload);
  }

  searchPlayers(query: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/admin/players/search`, { params: { q: query } });
  }

  getEligibleParticipants(playerId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/admin/players/${playerId}/eligible-participants`);
  }

  updateAssignment(playerId: number, participantId: number, amount: number): Observable<any> {
    return this.http.put(`${this.base}/api/admin/assignments/${playerId}`, { participantId, amount });
  }

  createUser(payload: { username: string; password: string; participantId?: number | null; participantName?: string | null; role?: string }): Observable<any> {
    return this.http.post(`${this.base}/api/admin/users`, payload);
  }

  getUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/admin/users`);
  }

  // 🔹 WEBSOCKET
  connectWebSocket(): WebSocket {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return new WebSocket(`${protocol}://${location.host}/ws/round`);
  }
}
