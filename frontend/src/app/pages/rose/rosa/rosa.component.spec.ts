import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { AuthService } from '../../../services/auth.service';
import { MercatoService } from '../../../services/mercato.service';
import { RosterService } from '../../../services/roster.service';
import { UserApiService } from '../../../services/user-api.service';
import { ActivatedRoute } from '@angular/router';

import { RosaComponent } from './rosa.component';

describe('RosaComponent', () => {
  let component: RosaComponent;
  let fixture: ComponentFixture<RosaComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [RosaComponent],
      providers: [
        { provide: RosterService, useValue: { getMyRoster: () => of([]), getGroupedRosters: () => of([]) } },
        { provide: MercatoService, useValue: { getConfig: () => of({ attiva: false }) } },
        { provide: AuthService, useValue: { hasRole: () => false } },
        { provide: MatSnackBar, useValue: { open: () => undefined } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(false) }) } },
        { provide: UserApiService, useValue: { getCurrentParticipant: () => of(null) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RosaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not expose the release column to participants', () => {
    component.isAdmin = false;
    expect(component.displayedColumns).toEqual(['team', 'player', 'amount']);
  });

  it('sorts roster players alphabetically by name', () => {
    const rosterService = TestBed.inject(RosterService) as jasmine.SpyObj<RosterService>;
    spyOn(rosterService, 'getMyRoster').and.returnValue(of([
      { playerName: 'Zortea' },
      { playerName: 'Buongiorno' },
      { playerName: 'gabbia' }
    ] as any));

    component.selectedParticipantId = 1;
    component.loadRoster();

    expect(component.roster.map(player => player.playerName)).toEqual(['Buongiorno', 'gabbia', 'Zortea']);
  });
});
