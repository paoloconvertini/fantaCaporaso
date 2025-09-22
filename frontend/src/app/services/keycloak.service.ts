import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class KeycloakService {
    private keycloak: any;
    private refreshInterval?: any;

    constructor(private router: Router) {
        this.keycloak = new (Keycloak as any)(environment.keycloak);
    }

    init(): Promise<boolean> {
        return this.keycloak.init({
            onLoad: 'login-required',
            checkLoginIframe: false
        }).then((authenticated: boolean) => {
            if (authenticated) {
                this.scheduleRefresh();
                this.handleRedirect();
            }
            return authenticated;
        });
    }

    getToken(): string | undefined {
        return this.keycloak.token;
    }

    private handleRedirect(): void {
        const tokenParsed: any = this.keycloak.tokenParsed;
        const roles: string[] = tokenParsed?.realm_access?.roles || [];

        if (roles.includes('admin')) {
            window.location.href = '/admin';
        } else {
            window.location.href = '/mobile';
        }

    }

    logout(): void {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }
        this.keycloak.logout({ redirectUri: window.location.origin });
    }

    isUserInRole(role: string): boolean {
        return this.keycloak.hasRealmRole(role);
    }

    // 🔹 Aggiorna il token periodicamente
    private scheduleRefresh(): void {
        this.refreshInterval = setInterval(() => {
            this.keycloak.updateToken(60) // refresh se mancano meno di 60s
                .then(refreshed => {
                    if (refreshed) {
                        console.debug('🔄 Token refresh eseguito');
                    }
                })
                .catch(() => {
                    console.warn('⚠️ Token refresh fallito, faccio logout');
                    this.logout();
                });
        }, 30000); // ogni 30 secondi
    }
}
