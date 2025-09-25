import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminComponent } from './pages/admin/admin.component';
import { MobileComponent } from './pages/mobile/mobile.component';
import { SummaryComponent } from './pages/summary/summary.component';
import { RostersComponent } from './pages/rose/rosters.component';
import { PlayersComponent } from './pages/players/players.component';
import { MobileRostersComponent } from './pages/mobile/mobile-rosters/mobile-rosters.component';
import { MobilePlayersComponent } from './pages/mobile/mobile-players/mobile-players.component';
import { UploadPlayersComponent } from './pages/upload-players/upload-players.component';
import { UploadRostersComponent } from './pages/upload-rosters/upload-rosters.component';
import { AppShellComponent } from './shared/app-shell/app-shell.component';

const routes: Routes = [
    {
        path: '',
        component: AppShellComponent,
        children: [
            // === AREA ADMIN ===
            { path: 'admin', component: AdminComponent },
            { path: 'rosters', component: RostersComponent },
            { path: 'players', component: PlayersComponent },
            { path: 'upload-rosters', component: UploadRostersComponent },
            { path: 'upload-players', component: UploadPlayersComponent },

            // === AREA MOBILE ===
            { path: 'mobile', component: MobileComponent },
            { path: 'mobile/rosters', component: MobileRostersComponent },
            { path: 'mobile/players', component: MobilePlayersComponent },

            // === SUMMARY (visibile solo se admin, controllato via roles/guard) ===
            { path: 'summary', component: SummaryComponent },

            // === Fallback ===
            { path: '**', redirectTo: 'mobile' }
        ]
    }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule {}
