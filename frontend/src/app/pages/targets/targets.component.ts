import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { PlayerTargetService } from '../../services/player-target.service';
import { UserApiService } from '../../services/user-api.service';

@Component({ selector: 'app-targets', templateUrl: './targets.component.html', styleUrls: ['./targets.component.css'] })
export class TargetsComponent implements OnInit, OnDestroy {
  analysis: any;
  selectedRole = 'DIFENSORE';
  error = '';
  loading = false;
  private subscriptions = new Subscription();
  private timer?: ReturnType<typeof setInterval>;
  constructor(public auth: AuthService, private targets: PlayerTargetService, private api: UserApiService) {}
  ngOnInit(): void {
    if (!this.auth.canUseTargets) return;
    this.refresh();
    this.subscriptions.add(this.targets.changes$.subscribe(() => this.refresh()));
    this.subscriptions.add(this.api.round$.subscribe(() => this.refresh()));
    this.subscriptions.add(this.api.summaryUpdated$.subscribe(() => this.refresh()));
    this.timer = setInterval(() => this.refresh(), 15000);
  }
  ngOnDestroy(): void { this.subscriptions.unsubscribe(); clearInterval(this.timer); }
  refresh(): void {
    if (this.loading || !this.auth.canUseTargets) return;
    this.loading = true;
    this.subscriptions.add(this.targets.analysis().subscribe({
      next: analysis => { this.analysis = analysis; this.loading = false; this.error = ''; },
      error: err => { this.error = err?.error?.message || 'Impossibile aggiornare l’analisi'; this.loading = false; }
    }));
  }
  get role(): any { return this.analysis?.roles.find((role: any) => role.role === this.selectedRole); }
  label(role: string): string {
    return ({ PORTIERE: 'Portieri', DIFENSORE: 'Difensori', CENTROCAMPISTA: 'Centrocampisti', ATTACCANTE: 'Attaccanti' } as Record<string, string>)[role] || role;
  }
}
