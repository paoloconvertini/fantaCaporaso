import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MercatoService } from '../../services/mercato.service';
import { AdminApiService } from '../../services/admin-api.service';

import { MercatoComponent } from './mercato.component';

describe('MercatoComponent', () => {
  let component: MercatoComponent;
  let fixture: ComponentFixture<MercatoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NoopAnimationsModule, MatButtonModule, MatFormFieldModule,
        MatInputModule, MatSelectModule, MatSlideToggleModule, MatCardModule, MatProgressSpinnerModule],
      declarations: [MercatoComponent],
      providers: [
        { provide: MercatoService, useValue: { getConfig: () => of({}) } },
        { provide: AdminApiService, useValue: {} },
        { provide: MatSnackBar, useValue: { open: () => undefined } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MercatoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('enables rendered confirmation buttons only when there are no errors', () => {
    component.departuresResult = { preview: true, players: [], errors: [] };
    component.rostersResult = { preview: true, errors: [], exchanges: [], releases: [] };
    fixture.detectChanges();
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const departures = buttons.find(button => button.textContent?.includes('Conferma giocatori partiti'))!;
    const rosters = buttons.find(button => button.textContent?.includes('Conferma correzioni, scambi e cessioni'))!;
    expect(departures.disabled).toBeFalse();
    expect(rosters.disabled).toBeFalse();
    component.departuresResult.errors = ['Proprietario non corrispondente'];
    component.rostersResult.errors = ['Squadra non riconosciuta'];
    fixture.detectChanges();
    expect(departures.disabled).toBeTrue();
    expect(rosters.disabled).toBeTrue();
  });

  it('blocks quotes before departure confirmation', () => {
    const api = TestBed.inject(AdminApiService);
    api.updateMarketPlayers = jasmine.createSpy('updateMarketPlayers');
    component.quotesFile = new File(['quotes'], 'quotes.xlsx');
    component.form.patchValue({ partitiImportati: false });
    component.previewQuotes();
    expect(api.updateMarketPlayers).not.toHaveBeenCalled();
    component.quotesResult = { preview: true };
    component.confirmQuotes();
    expect(api.updateMarketPlayers).not.toHaveBeenCalled();
  });

  it('shows reserve corrections separately from releases', () => {
    component.rostersResult = {
      preview: true, errors: [], exchanges: [], releases: [],
      goalkeeperCorrections: ['Squadra: Vecchio → Nuovo (costo storico 1, crediti invariati, nessun cambio porta)']
    };
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Correzione pacchetto portieri: Squadra: Vecchio → Nuovo');
    expect(fixture.nativeElement.textContent).toContain('nessun cambio porta');
  });

  it('refreshes confirmed departure state from the server', () => {
    const api = TestBed.inject(AdminApiService);
    api.importMarketDepartures = jasmine.createSpy('importMarketDepartures')
      .and.returnValue(of({ preview: false, players: [], errors: [] }));
    const reload = spyOn(component, 'loadConfig');
    component.departuresFile = new File(['departures'], 'partiti.xlsx');
    component.departuresResult = { preview: true, errors: [] };
    component.importDepartures(true);
    expect(api.importMarketDepartures).toHaveBeenCalledWith(component.departuresFile, true);
    expect(reload).toHaveBeenCalled();
  });

  it('blocks confirmation with departure errors', () => {
    const api = TestBed.inject(AdminApiService);
    api.importMarketDepartures = jasmine.createSpy('importMarketDepartures');
    component.departuresFile = new File(['departures'], 'partiti.xlsx');
    component.departuresResult = { preview: true, errors: ['Proprietario non corrispondente'] };
    component.importDepartures(true);
    expect(api.importMarketDepartures).not.toHaveBeenCalled();
  });
});
