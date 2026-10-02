import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class MiniAuctionApiService {
  private base = (window as any).__API_BASE__ || '';
  constructor(private http: HttpClient) {}
  current() { return this.http.get<any>(`${this.base}/api/mini-auctions/current`); }
  sources() { return this.http.get<any[]>(`${this.base}/api/mini-auctions/sources`); }
  candidates(sourceSessionCode: string, sourceDate: string) {
    return this.http.get<any[]>(`${this.base}/api/mini-auctions/candidates`, { params: { sourceSessionCode, sourceDate } });
  }
  prepare(data: any) { return this.http.post<any>(`${this.base}/api/mini-auctions/prepare`, data); }
  activate(id: number) { return this.http.post<any>(`${this.base}/api/mini-auctions/${id}/activate`, { confirm: true }); }
  finish(id: number) { return this.http.post<any>(`${this.base}/api/mini-auctions/${id}/finish`, {}); }
}
