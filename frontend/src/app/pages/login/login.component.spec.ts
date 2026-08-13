import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login', 'changePassword'], { user: null });
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);

    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [LoginComponent],
      providers: [
        FormBuilder,
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: MatSnackBar, useValue: jasmine.createSpyObj('MatSnackBar', ['open']) }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('does not submit an incomplete login form', () => {
    component.login();
    expect(auth.login).not.toHaveBeenCalled();
    expect(component.form.touched).toBeTrue();
  });

  it('redirects an admin after login', () => {
    auth.login.and.returnValue(of({ username: 'paolo', roles: ['admin'], mustChangePassword: false }));
    component.form.setValue({ username: 'paolo', password: 'password' });

    component.login();

    expect(auth.login).toHaveBeenCalledWith('paolo', 'password');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
  });

  it('shows the password change form when required', () => {
    auth.login.and.returnValue(of({ username: 'utente', roles: ['password-change'], mustChangePassword: true }));
    component.form.setValue({ username: 'utente', password: 'temporanea' });

    component.login();

    expect(component.mustChangePassword).toBeTrue();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
