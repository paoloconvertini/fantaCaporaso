import { Router } from '@angular/router';
import { AdminOnlyGuard, AuthGuard, UserOnlyGuard } from './role-redirect.guard';

describe('authentication guards', () => {
  const router = { parseUrl: (url: string) => url } as unknown as Router;

  it('redirects anonymous users to login', () => {
    const auth = { isAuthenticated: false } as any;
    expect(new AuthGuard(auth, router).canActivate()).toBe('/login' as any);
    expect(new AdminOnlyGuard(auth, router).canActivate()).toBe('/login' as any);
  });

  it('keeps admin and participant areas separated', () => {
    const admin = { isAuthenticated: true, isAdmin: true } as any;
    const user = { isAuthenticated: true, isAdmin: false } as any;

    expect(new AdminOnlyGuard(admin, router).canActivate()).toBeTrue();
    expect(new AdminOnlyGuard(user, router).canActivate()).toBe('/mobile' as any);
    expect(new UserOnlyGuard(admin, router).canActivate()).toBe('/admin' as any);
    expect(new UserOnlyGuard(user, router).canActivate()).toBeTrue();
  });

  it('redirects a temporary-password session to login', () => {
    const auth = { isAuthenticated: true, user: { mustChangePassword: true } } as any;
    expect(new AuthGuard(auth, router).canActivate()).toBe('/login' as any);
  });
});
