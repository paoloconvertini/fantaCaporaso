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
import {RosaComponent} from "./pages/rose/rosa/rosa.component";
import {MercatoComponent} from "./pages/mercato/mercato.component";
import { AdminOnlyGuard, AuthGuard, UserOnlyGuard } from './guards/role-redirect.guard';
import { HomeComponent } from './pages/home/home.component';
import { environment } from '../environments/environment';
import { AdminUsersComponent } from './pages/admin-users/admin-users.component';
import { LoginComponent } from './pages/login/login.component';

const routes: Routes = [
    { path: 'login', component: LoginComponent },
    {
        path: '',
        component: AppShellComponent,
        canActivate: [AuthGuard],
        children: [
            { path: '', component: HomeComponent, pathMatch: 'full' },
            { path: 'admin', component: AdminComponent, canActivate: [AdminOnlyGuard] },
            { path: 'rosters', component: RostersComponent },
            { path: 'players', component: PlayersComponent },
            { path: 'upload-rosters', component: UploadRostersComponent },
            { path: 'upload-players', component: UploadPlayersComponent },
            { path: 'rosa', component: RosaComponent },
            { path: 'admin/mercato', component: MercatoComponent, canActivate: [AdminOnlyGuard] },
            { path: 'admin/users', component: AdminUsersComponent, canActivate: [AdminOnlyGuard] },

            { path: 'mobile', component: MobileComponent, canActivate: [UserOnlyGuard] },
            { path: 'mobile/rosters', component: MobileRostersComponent },
            { path: 'mobile/players', component: MobilePlayersComponent },

            { path: 'summary', component: SummaryComponent }
        ]
    }
];

@NgModule({
    imports: [RouterModule.forRoot(routes, { useHash: environment.useHashRouting })],
    exports: [RouterModule]
})
export class AppRoutingModule {}
