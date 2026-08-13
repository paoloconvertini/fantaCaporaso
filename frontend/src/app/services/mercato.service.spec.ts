import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { MercatoService } from './mercato.service';

describe('MercatoService', () => {
  let service: MercatoService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(MercatoService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
