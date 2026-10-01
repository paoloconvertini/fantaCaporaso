import { of } from 'rxjs';
import { AuthService } from './auth.service';

describe('Observer target access', () => {
  function loggedIn(role: string, participantId?: number): AuthService {
    const service = new AuthService({ post: () => of({ body: { username: 'test', roles: [role], participantId } }) } as any);
    service.login('test', 'unused').subscribe();
    return service;
  }
  it('enables targets only for an associated observer', () => {
    expect(loggedIn('observer', 7).canUseTargets).toBeTrue();
    expect(loggedIn('observer').canUseTargets).toBeFalse();
    expect(loggedIn('user', 7).canUseTargets).toBeFalse();
  });
  it('does not confuse an associated observer with a bidding user', () => {
    const observer = loggedIn('observer', 7);
    expect(observer.isObserver).toBeTrue();
    expect(observer.isUser).toBeFalse();
    expect(loggedIn('user', 7).isObserver).toBeFalse();
  });
});
