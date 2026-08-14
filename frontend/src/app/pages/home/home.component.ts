import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  template: `
    <div class="home-container">
      <mat-card class="home-card">
        <h2>FantaCaporaso</h2>

        <p class="home-subtitle">
          {{ auth.isAdmin ? 'Gestione asta' : 'Area partecipante' }}
        </p>

        <a
          *ngIf="auth.isAdmin; else userHome"
          mat-raised-button
          color="primary"
          routerLink="/admin"
        >
          Vai alla gestione asta
        </a>

        <ng-template #userHome>
          <a mat-raised-button color="primary" routerLink="/mobile">
            Vai alla puntata
          </a>
        </ng-template>
      </mat-card>
    </div>
  `,
  styles: [`
    .home-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: calc(100vh - 64px);
      padding: 24px;
    }

    .home-card {
      width: 100%;
      max-width: 420px;
      text-align: center;
      padding: 24px;
    }

    .home-subtitle {
      margin: 8px 0 24px;
      color: rgba(0, 0, 0, 0.65);
    }

  `]
})
export class HomeComponent {
  constructor(public auth: AuthService) {}
}
