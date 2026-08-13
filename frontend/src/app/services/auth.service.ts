import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

export type AuthUser = {
  username: string;
  roles: string[];
  participantId?: number;
  participantName?: string;
  mustChangePassword?: boolean;
};

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private base = (window as any).__API_BASE__ || '';
  private currentUser$ = new BehaviorSubject<AuthUser | null>(null);

  constructor(private http: HttpClient) {}

  init(): Promise<boolean> {
    return this.loadMe().toPromise().then(() => true).catch(() => true);
  }

  login(username: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthUser>(
      `${this.base}/api/auth/login`,
      { username, password },
      { observe: 'response', withCredentials: true }
    ).pipe(
      map((response: HttpResponse<AuthUser>) => {
        return response.body as AuthUser;
      }),
      tap(user => this.currentUser$.next(user))
    );
  }

  changePassword(password: string): Observable<AuthUser> {
    return this.http.post<AuthUser>(
      `${this.base}/api/auth/password`, { password }, { withCredentials: true }
    ).pipe(tap(user => this.currentUser$.next(user)));
  }

  logout(): void {
    this.currentUser$.next(null);
    this.http.post(`${this.base}/api/auth/logout`, {}, { withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => window.location.href = '/login');
  }

  loadMe(): Observable<AuthUser | null> {
    return this.http.get<AuthUser>(`${this.base}/api/auth/me`, { withCredentials: true }).pipe(
      tap(user => this.currentUser$.next(user)),
      catchError(() => {
        this.currentUser$.next(null);
        return of(null);
      })
    );
  }

  get user(): AuthUser | null {
    return this.currentUser$.value;
  }

  get username(): string {
    return this.user?.username || '';
  }

  get roles(): string[] {
    return this.user?.roles || [];
  }

  hasRole(role: string): boolean {
    return this.roles.includes(role);
  }

  get isAuthenticated(): boolean {
    return !!this.user;
  }

  get isAdmin(): boolean {
    return this.hasRole('admin');
  }

  get isUser(): boolean {
    return this.hasRole('user');
  }

  getDebugInfo(): { username: string; roles: string[] } {
    return {
      username: this.username,
      roles: this.roles
    };
  }
}
