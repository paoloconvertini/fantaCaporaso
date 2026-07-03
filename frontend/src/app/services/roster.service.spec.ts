import { TestBed } from '@angular/core/testing';

import { RoasterServiceService } from './roster.service';

describe('RoasterServiceService', () => {
  let service: RoasterServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(RoasterServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
