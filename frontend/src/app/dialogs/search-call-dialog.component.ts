import { Component, OnDestroy } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { Subscription, switchMap, timer } from 'rxjs';
import { AdminApiService } from '../services/admin-api.service';

@Component({
  selector: 'app-search-call-dialog',
  templateUrl: './search-call-dialog.component.html',
  styleUrls: ['./manual-assign-dialog.component.css', './search-call-dialog.component.css']
})
export class SearchCallDialogComponent implements OnDestroy {
  query = '';
  role = '';
  players: any[] = [];
  loading = false;
  selecting = false;
  error = '';
  private search?: Subscription;

  constructor(private api: AdminApiService, private ref: MatDialogRef<SearchCallDialogComponent>) {}

  searchPlayers() {
    this.search?.unsubscribe();
    this.players = [];
    this.error = '';
    this.loading = false;
    const query = this.query.trim();
    const role = this.role;
    if (query.length < 2) return;
    this.loading = true;
    this.search = timer(300).pipe(
      switchMap(() => this.api.searchCallPlayers(query, role))
    ).subscribe({
      next: players => { this.players = players; this.loading = false; },
      error: () => { this.error = 'Ricerca non riuscita. Riprova.'; this.loading = false; }
    });
  }

  select(player: any) {
    if (this.selecting) return;
    this.selecting = true;
    this.ref.disableClose = true;
    this.error = '';
    this.api.selectCallPlayer(player.id).subscribe({
      next: selected => this.ref.close(selected),
      error: err => {
        this.selecting = false;
        this.ref.disableClose = false;
        this.error = err.error?.error || 'Chiamata non disponibile. Aggiorna la ricerca.';
      }
    });
  }

  ngOnDestroy() { this.search?.unsubscribe(); }
}
