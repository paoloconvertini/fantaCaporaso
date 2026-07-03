import { TestBed } from '@angular/core/testing';

import { MercatoServiceService } from './mercato.service';

describe('MercatoServiceService', () => {
  let service: MercatoServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MercatoServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
