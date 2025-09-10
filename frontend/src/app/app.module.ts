import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatRadioModule } from '@angular/material/radio';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { ManualAssignDialogComponent } from './dialogs/manual-assign-dialog.component';
import { AppComponent } from './app.component';
import { AdminComponent } from './pages/admin/admin.component';
import { MobileComponent } from './pages/mobile/mobile.component';
import { HomeComponent } from './home/home.component';
import { AppRoutingModule } from './app-routing.module';
import {HttpClientModule} from "@angular/common/http";
import {SummaryComponent} from "./pages/summary/summary.component";
import {MatProgressBarModule} from "@angular/material/progress-bar";
import {MatIconModule} from "@angular/material/icon";
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatDialogModule} from "@angular/material/dialog";
import {MatProgressSpinnerModule} from "@angular/material/progress-spinner";
import {MatSidenavModule} from "@angular/material/sidenav";
import {RostersComponent} from "./pages/rose/rosters.component";
import {PlayersComponent} from "./pages/players/players.component";
import {MobilePlayersComponent} from "./pages/mobile/mobile-players/mobile-players.component";
import {MobileRostersComponent} from "./pages/mobile/mobile-rosters/mobile-rosters.component";
import {MobileShellComponent} from "./pages/mobile/mobile-shell/mobile-shell.component";
import {MatToolbarModule} from "@angular/material/toolbar";
import {MatListModule} from "@angular/material/list";   // 👈 routing centralizzato

@NgModule({
  declarations: [
    AppComponent,
    AdminComponent,
    MobileComponent,
    HomeComponent,
    SummaryComponent,
      ManualAssignDialogComponent, RostersComponent, PlayersComponent
      , MobilePlayersComponent, MobileRostersComponent, MobileShellComponent
  ],
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        FormsModule,
        AppRoutingModule,   // 👈 usi solo questo, niente RouterModule.forRoot()
        MatButtonModule,
        MatCardModule,
        MatInputModule,
        MatSelectModule,
        MatTableModule,
        MatRadioModule,
        MatButtonToggleModule,
        HttpClientModule,
        MatProgressBarModule,
        MatIconModule,
        MatTooltipModule,
        MatDialogModule,
        MatProgressSpinnerModule,
        MatSidenavModule,
        MatToolbarModule,
        MatListModule
    ],
  bootstrap: [AppComponent]
})
export class AppModule {}
