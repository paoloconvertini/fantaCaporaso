import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { MercatoService } from '../../services/mercato.service';

import { MercatoComponent } from './mercato.component';

describe('MercatoComponent', () => {
  let component: MercatoComponent;
  let fixture: ComponentFixture<MercatoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [MercatoComponent],
      providers: [
        { provide: MercatoService, useValue: { getConfig: () => of({}) } },
        { provide: MatSnackBar, useValue: { open: () => undefined } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .overrideComponent(MercatoComponent, { set: { template: '' } })
    .compileComponents();

    fixture = TestBed.createComponent(MercatoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
