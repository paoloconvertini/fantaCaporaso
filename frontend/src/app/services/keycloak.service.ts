import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';
import { Router } from '@angular/router';

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
                this.handleRedirect();
                this.scheduleRefresh();
            }
            return authenticated;
        }).catch((err: any) => {
            return false;
        });
    }

    getUsername(): string {
        return this.keycloak?.tokenParsed?.preferred_username || '';
    }

    getToken(): string | undefined {
        return this.keycloak.token;
    }

    getRoles(): string[] {
        return this.keycloak.tokenParsed?.realm_access?.roles || [];
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

    private scheduleRefresh(): void {
        this.refreshInterval = setInterval(() => {
            this.keycloak.updateToken(60)
                .catch(() => this.logout());
        }, 30000);
    }

    private handleRedirect(): void {
        const roles: string[] = this.getRoles();

        if (roles.includes('admin')) {
            this.router.navigateByUrl('/admin');
        } else {
            this.router.navigateByUrl('/mobile');
        }
    }
}
