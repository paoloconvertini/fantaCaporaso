import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatRadioModule } from '@angular/material/radio';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';

import { AppComponent } from './app.component';
import { AdminComponent } from './pages/admin/admin.component';
import { MobileComponent } from './pages/mobile/mobile.component';
import { SummaryComponent } from './pages/summary/summary.component';
import { RostersComponent } from './pages/rose/rosters.component';
import { PlayersComponent } from './pages/players/players.component';
import { MobilePlayersComponent } from './pages/mobile/mobile-players/mobile-players.component';
import { MobileRostersComponent } from './pages/mobile/mobile-rosters/mobile-rosters.component';
import { MobileShellComponent } from './pages/mobile/mobile-shell/mobile-shell.component';
import { UploadPlayersComponent } from './pages/upload-players/upload-players.component';
import { UploadRostersComponent } from './pages/upload-rosters/upload-rosters.component';
import { AdminMenuComponent } from './shared/admin-menu/admin-menu.component';
import { ManualAssignDialogComponent } from './dialogs/manual-assign-dialog.component';

import { AppRoutingModule } from './app-routing.module';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { KeycloakService } from './services/keycloak.service';
import { ToolbarComponent } from './shared/toolbar/toolbar.component';

// 👉 funzione factory per APP_INITIALIZER
export function initializeKeycloak(keycloak: KeycloakService) {
    return () => keycloak.init();
}

@NgModule({
    declarations: [
        AppComponent,
        AdminComponent,
        MobileComponent,
        SummaryComponent,
        RostersComponent,
        PlayersComponent,
        MobilePlayersComponent,
        MobileRostersComponent,
        MobileShellComponent,
        UploadPlayersComponent,
        UploadRostersComponent,
        AdminMenuComponent,
        ManualAssignDialogComponent,
        ToolbarComponent,
    ],
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        FormsModule,
        HttpClientModule,
        AppRoutingModule,
        MatButtonModule,
        MatCardModule,
        MatInputModule,
        MatSelectModule,
        MatTableModule,
        MatRadioModule,
        MatButtonToggleModule,
        MatProgressBarModule,
        MatIconModule,
        MatTooltipModule,
        MatDialogModule,
        MatProgressSpinnerModule,
        MatSidenavModule,
        MatToolbarModule,
        MatListModule
    ],
    providers: [
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
        {
            provide: APP_INITIALIZER,
            useFactory: initializeKeycloak,
            deps: [KeycloakService],
            multi: true
        }
    ],
    bootstrap: [AppComponent]
})
export class AppModule {}
