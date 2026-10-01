import { Component, Input, OnInit } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { PlayerTargetService } from '../../services/player-target.service';

@Component({
  selector: 'app-target-button',
  template: `<button *ngIf="auth.canUseTargets" mat-button type="button"
    [class.selected]="targets.selected.has(playerId)" [disabled]="targets.pending.has(playerId)"
    [attr.aria-pressed]="targets.selected.has(playerId)" (click)="targets.toggle(playerId)">
    <mat-icon>{{ targets.selected.has(playerId) ? 'star' : 'star_border' }}</mat-icon>
    {{ removeOnly ? 'Rimuovi' : (targets.selected.has(playerId) ? 'Rimuovi dagli obiettivi' : 'Aggiungi agli obiettivi') }}
  </button>`,
  styles: [`button { color: #81d9b2; } button.selected { color: #ffdc80; } mat-icon { font-size: 20px; }`]
})
export class TargetButtonComponent implements OnInit {
  @Input() playerId!: number;
  @Input() removeOnly = false;
  constructor(public auth: AuthService, public targets: PlayerTargetService) {}
  ngOnInit(): void { if (this.auth.canUseTargets) this.targets.loadOnce(); }
}
