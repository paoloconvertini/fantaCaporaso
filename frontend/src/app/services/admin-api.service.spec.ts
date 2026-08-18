import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AdminApiService } from './admin-api.service';

describe('AdminApiService', () => {
  let service: AdminApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AdminApiService);
    http = TestBed.inject(HttpTestingController);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('searches players for the admin assignment dialog', () => {
    service.searchPlayers('milan').subscribe();
    const request = http.expectOne(req => req.url === '/api/admin/players/search' && req.params.get('q') === 'milan');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('updates an assignment by player id', () => {
    service.updateAssignment(10, 2, 35).subscribe();
    const request = http.expectOne('/api/admin/assignments/10');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ participantId: 2, amount: 35 });
    request.flush({});
  });

  it('loads participants eligible for the selected player', () => {
    service.getEligibleParticipants(10).subscribe();
    const request = http.expectOne('/api/admin/players/10/eligible-participants');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('uses the safe round endpoint to skip a player', () => {
    service.randomSkip('Giocatore', 'Roma').subscribe();
    const request = http.expectOne('/api/round/skip');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Giocatore', team: 'Roma' });
    request.flush({});
  });
});
