import { Component, OnInit } from '@angular/core';
import { UserApiService } from '../../services/user-api.service';

@Component({ selector: 'app-auction-history', templateUrl: './auction-history.component.html', styleUrls: ['./auction-history.component.css'] })
export class AuctionHistoryComponent implements OnInit {
  items: any[] = [];
  total = 0;
  page = 0;
  size = 30;
  query = '';
  role = '';
  sort = 'date';
  loading = false;
  private searchTimer?: ReturnType<typeof setTimeout>;
  constructor(private api: UserApiService) {}
  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading = true;
    this.api.getAuctionHistory({ q: this.query, role: this.role, sort: this.sort, page: this.page, size: this.size }).subscribe({
      next: result => { this.items = result.items; this.total = result.total; this.loading = false; },
      error: () => { this.items = []; this.total = 0; this.loading = false; }
    });
  }
  search(): void { clearTimeout(this.searchTimer); this.page = 0; this.searchTimer = setTimeout(() => this.load(), 250); }
  previous(): void { if (this.page > 0) { this.page--; this.load(); } }
  next(): void { if ((this.page + 1) * this.size < this.total) { this.page++; this.load(); } }
}
