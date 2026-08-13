import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { AdminApiService } from '../../services/admin-api.service';

import { UploadRostersComponent } from './upload-rosters.component';

describe('UploadRostersComponent', () => {
  let component: UploadRostersComponent;
  let fixture: ComponentFixture<UploadRostersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [UploadRostersComponent],
      providers: [{ provide: AdminApiService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UploadRostersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
