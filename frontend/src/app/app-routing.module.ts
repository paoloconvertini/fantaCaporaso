import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminComponent } from './pages/admin/admin.component';
import { MobileComponent } from './pages/mobile/mobile.component';
import {SummaryComponent} from "./pages/summary/summary.component";
import {RostersComponent} from "./pages/rose/rosters.component";
import {PlayersComponent} from "./pages/players/players.component";
import {MobileRostersComponent} from "./pages/mobile/mobile-rosters/mobile-rosters.component";
import {MobilePlayersComponent} from "./pages/mobile/mobile-players/mobile-players.component";
import {MobileShellComponent} from "./pages/mobile/mobile-shell/mobile-shell.component";
import {UploadPlayersComponent} from "./pages/upload-players/upload-players.component";
import {UploadRostersComponent} from "./pages/upload-rosters/upload-rosters.component";

const routes: Routes = [

    // === AREA ADMIN ===
    { path: 'admin', component: AdminComponent },
    { path: 'rosters', component: RostersComponent },
    { path: 'players', component: PlayersComponent },
    { path: 'upload-rosters', component: UploadRostersComponent },
    { path: 'upload-players', component: UploadPlayersComponent },


    // === AREA MOBILE con shell (hamburger + sidenav) ===
    {
        path: 'mobile',
        component: MobileShellComponent,
        children: [
            { path: '', component: MobileComponent },               // asta corrente
            { path: 'rosters', component: MobileRostersComponent }, // rose
            { path: 'players', component: MobilePlayersComponent }  // calciatori
        ]
    },

    { path: 'summary', component: SummaryComponent },

    // fallback
    { path: '**', redirectTo: '' },
];



@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule {}
