import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class KeycloakService {
    private keycloak: any;
    private refreshInterval?: any;
    private username = '';

    constructor() {
        this.installDevCryptoFallback();
        this.keycloak = new (Keycloak as any)(environment.keycloak);
    }

    /** 🔹 Inizializza Keycloak e gestisce redirect + refresh token */
    init(): Promise<boolean> {
        const pkceMethod = environment.production ? 'S256' : false;
        const redirectUri = this.currentAppUrl();

        return this.keycloak.init({
            onLoad: 'login-required',
            redirectUri,
            pkceMethod,
            checkLoginIframe: false
        }).then(async (authenticated: boolean) => {

            if (!authenticated) {
                await this.keycloak.login({ redirectUri, pkceMethod });
                return false;
            }

            await this.loadUsername();
            this.scheduleRefresh();

            return authenticated;
        }).catch((err: any) => {
            console.error('KEYCLOAK INIT ERROR', err);
            const errorDetails = (() => {
                try {
                    return JSON.stringify(err, Object.getOwnPropertyNames(err), 2);
                } catch {
                    return String(err);
                }
            })();

            document.body.innerHTML = `
                <div style="padding:24px;font-family:Arial,sans-serif;color:#e2e8f0">
                    <h2>Errore login</h2>
                    <p>Keycloak non ha completato l'autenticazione.</p>
                    <p><strong>Origin:</strong> ${window.location.origin}</p>
                    <p><strong>Keycloak:</strong> ${environment.keycloak.url}</p>
                    <p><strong>PKCE:</strong> ${String(pkceMethod)}</p>
                    <p><strong>Secure context:</strong> ${String(window.isSecureContext)}</p>
                    <pre style="white-space:pre-wrap;color:#fca5a5">${err?.message || errorDetails || 'Errore non disponibile'}</pre>
                </div>
            `;
            throw err;
        });
    }

    /** 🔹 Username utente loggato */
    getUsername(): string {
        return this.username || this.keycloak?.tokenParsed?.preferred_username || '';
    }

    /** 🔹 Token JWT corrente */
    getToken(): string | undefined {
        return this.keycloak.token;
    }

    async getValidToken(): Promise<string | undefined> {
        if (!this.keycloak?.token) {
            return undefined;
        }

        try {
            await this.keycloak.updateToken(30);
        } catch (err) {
            console.error('TOKEN UPDATE ERROR', err);
        }

        return this.keycloak.token;
    }

    /** 🔹 Ruoli presenti nel token */
    getRoles(): string[] {
        return this.keycloak.tokenParsed?.realm_access?.roles || [];
    }

    getDebugInfo(): { username: string; roles: string[] } {
        return {
            username: this.getUsername(),
            roles: this.getRoles()
        };
    }

    /** 🔹 Logout + pulizia refresh */
    logout(): void {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }
        this.keycloak.logout({ redirectUri: window.location.origin });
    }

    loginCurrentPage(): void {
        const pkceMethod = environment.production ? 'S256' : false;
        this.keycloak.login({ redirectUri: this.currentAppUrl(), pkceMethod });
    }

    /** 🔹 Controlla se l’utente ha un ruolo specifico */
    isUserInRole(role: string): boolean {
        return this.getRoles().includes(role);
    }

    /** 🔹 Alias per compatibilità */
    hasRole(role: string): boolean {
        return this.isUserInRole(role);
    }

    /** 🔹 Getter rapidi */
    get isAdmin(): boolean {
        return this.isUserInRole('admin');
    }

    get isUser(): boolean {
        return this.isUserInRole('user');
    }

    /** 🔹 Refresh token automatico ogni 30s */
    private scheduleRefresh(): void {
        this.refreshInterval = setInterval(() => {
            this.keycloak.updateToken(60)
                .then((refreshed: boolean) => {
                    console.log('TOKEN REFRESH', refreshed);
                })
                .catch((err: any) => {
                    console.error('REFRESH ERROR', err);
                });
        }, 30000);
    }

    private async loadUsername(): Promise<void> {
        this.username = this.keycloak?.tokenParsed?.preferred_username || '';

        if (this.username || !this.keycloak?.loadUserProfile) {
            return;
        }

        try {
            const profile = await this.keycloak.loadUserProfile();
            this.username = profile?.username || '';
        } catch {
            this.username = '';
        }
    }

    private installDevCryptoFallback(): void {
        if (environment.production || window.isSecureContext) {
            return;
        }

        const cryptoRef = (window as any).crypto || {};
        if (cryptoRef.randomUUID) {
            return;
        }

        cryptoRef.randomUUID = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
            const random = Math.floor(Math.random() * 16);
            const value = char === 'x' ? random : (random & 0x3) | 0x8;
            return value.toString(16);
        });

        if (!(window as any).crypto) {
            Object.defineProperty(window, 'crypto', {
                value: cryptoRef,
                configurable: true
            });
        }
    }

    private currentAppUrl(): string {
        const url = new URL(window.location.href);

        ['code', 'state', 'session_state', 'iss'].forEach(param => {
            url.searchParams.delete(param);
        });
        if (environment.useHashRouting && (!url.hash || url.hash === '#')) {
            url.hash = '#/';
        }
        return url.toString();
    }

}
