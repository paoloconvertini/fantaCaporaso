import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stores the authenticated user after login', () => {
    service.login('paolo', 'secret').subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual({ username: 'paolo', password: 'secret' });
    request.flush({ username: 'paolo', roles: ['admin'] });

    expect(service.isAuthenticated).toBeTrue();
    expect(service.isAdmin).toBeTrue();
  });

  it('clears stale identity when /me is unauthorized', () => {
    service.login('paolo', 'secret').subscribe();
    http.expectOne('/api/auth/login').flush({ username: 'paolo', roles: ['admin'] });

    service.loadMe().subscribe(user => expect(user).toBeNull());
    http.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(service.isAuthenticated).toBeFalse();
  });

  it('replaces the restricted identity after the mandatory password change', () => {
    service.login('paolo', 'temp1234').subscribe();
    http.expectOne('/api/auth/login').flush({
      username: 'paolo', roles: ['password-change'], mustChangePassword: true
    });

    service.changePassword('personal123').subscribe();
    const request = http.expectOne('/api/auth/password');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual({ password: 'personal123' });
    request.flush({ username: 'paolo', roles: ['user'], mustChangePassword: false });

    expect(service.isUser).toBeTrue();
    expect(service.user?.mustChangePassword).toBeFalse();
  });
});
