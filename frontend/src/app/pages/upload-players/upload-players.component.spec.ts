import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { AdminApiService } from '../../services/admin-api.service';

import { UploadPlayersComponent } from './upload-players.component';

describe('UploadPlayersComponent', () => {
  let component: UploadPlayersComponent;
  let fixture: ComponentFixture<UploadPlayersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [UploadPlayersComponent],
      providers: [{ provide: AdminApiService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UploadPlayersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
