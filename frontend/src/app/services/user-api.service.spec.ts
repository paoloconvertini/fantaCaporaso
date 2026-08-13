import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { UserApiService } from './user-api.service';

describe('UserApiService', () => {
  let service: UserApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(UserApiService);
    http = TestBed.inject(HttpTestingController);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('hydrates the shared round state from the backend', () => {
    service.refreshRound();

    http.expectOne('/api/round').flush({ roundId: 'round-live', closed: false, playerRole: 'DIFENSORE', bids: {}, bidders: ['Squadra'] });

    expect(service.round$.value.roundId).toBe('round-live');
    expect(service.roleFilter$.value).toBe('DIFENSORE');
    expect(service.activeUsers$.value).toEqual(['Squadra']);
  });
});
