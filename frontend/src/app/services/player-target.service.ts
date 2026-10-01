import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PlayerTargetService {
  private base = (window as any).__API_BASE__ || '';
  selected = new Set<number>();
  pending = new Set<number>();
  readonly changes$ = new Subject<void>();
  private loaded = false;
  private loading = false;
  constructor(private http: HttpClient, private snack: MatSnackBar) {}

  loadOnce(): void {
    if (this.loaded || this.loading) return;
    this.loading = true;
    this.http.get<number[]>(`${this.base}/api/targets`).subscribe({
      next: ids => { this.selected = new Set(ids); this.loaded = true; this.loading = false; },
      error: () => { this.loading = false; this.snack.open('Impossibile leggere gli obiettivi', 'Chiudi', { duration: 3000 }); }
    });
  }

  toggle(id: number): void {
    if (this.pending.has(id) || !this.loaded) return;
    const selected = this.selected.has(id);
    this.pending.add(id);
    const request = selected ? this.http.delete<void>(`${this.base}/api/targets/${id}`) : this.http.put<void>(`${this.base}/api/targets/${id}`, {});
    request.subscribe({
      next: () => {
        selected ? this.selected.delete(id) : this.selected.add(id);
        this.pending.delete(id); this.loaded = false; this.loadOnce(); this.changes$.next();
      },
      error: err => {
        this.pending.delete(id);
        this.snack.open(err?.error?.message || 'Impossibile aggiornare l’obiettivo', 'Chiudi', { duration: 3500 });
      }
    });
  }

  analysis() { return this.http.get<any>(`${this.base}/api/targets/analysis`); }
}
