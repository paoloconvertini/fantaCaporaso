import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { AuthService } from '../../services/auth.service';

import { AppShellComponent } from './app-shell.component';

describe('AppShellComponent', () => {
  let component: AppShellComponent;
  let fixture: ComponentFixture<AppShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [AppShellComponent],
      providers: [{ provide: AuthService, useValue: { roles: ['admin'] } }],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AppShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('recognizes the admin role', () => {
    expect(component.isAdmin).toBeTrue();
    expect(component.isParticipant).toBeFalse();
  });

  it('shows the current auction entry to participants', () => {
    component.isAdmin = false;
    component.isUser = true;
    component.isParticipant = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Asta corrente');
  });

  it('shows the auction but hides the personal roster for observers', () => {
    component.isAdmin = false;
    component.isUser = true;
    component.isParticipant = false;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Asta corrente');
    expect(fixture.nativeElement.textContent).not.toContain('Rosa');
  });

  it('keeps the permanent desktop menu open when navigating', () => {
    component.menuMode = 'side';
    component.menu = jasmine.createSpyObj('MatSidenav', ['close']);

    component.closeMenuOnMobile();

    expect(component.menu.close).not.toHaveBeenCalled();
  });

  it('closes the overlay menu after mobile navigation', () => {
    component.menuMode = 'over';
    component.menu = jasmine.createSpyObj('MatSidenav', ['close']);

    component.closeMenuOnMobile();

    expect(component.menu.close).toHaveBeenCalled();
  });
});
