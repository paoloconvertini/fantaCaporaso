import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { Router } from '@angular/router';
import { UserApiService } from '../../services/user-api.service';
import { SummaryComponent } from './summary.component';

describe('SummaryComponent', () => {
    let fixture: ComponentFixture<SummaryComponent>;
    let component: SummaryComponent;
    const navigate = jasmine.createSpy('navigate');

    beforeEach(async () => {
        navigate.calls.reset();
        await TestBed.configureTestingModule({
            declarations: [SummaryComponent],
            providers: [
                {
                    provide: UserApiService,
                    useValue: { getSummary: () => of([]), summaryUpdated$: of() }
                },
                { provide: Router, useValue: { navigate } }
            ],
            schemas: [NO_ERRORS_SCHEMA]
        }).compileComponents();

        fixture = TestBed.createComponent(SummaryComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('opens the selected participant roster', () => {
        component.openRoster({ id: 7 } as any);

        expect(navigate).toHaveBeenCalledWith(['/rosa'], {
            queryParams: { participantId: 7 }
        });
    });
});
