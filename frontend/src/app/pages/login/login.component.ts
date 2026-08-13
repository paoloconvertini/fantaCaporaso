import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  form: FormGroup;
  passwordForm: FormGroup;
  loading = false;
  mustChangePassword = false;
  hidePassword = true;
  hideNewPassword = true;
  hideConfirmPassword = true;

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.form = this.fb.group({
      username: ['', Validators.required],
      password: ['', Validators.required]
    });
    this.passwordForm = this.fb.group({
      password: ['', [Validators.required, Validators.minLength(4)]],
      confirmPassword: ['', Validators.required]
    });
    this.mustChangePassword = !!this.auth.user?.mustChangePassword;
  }

  login(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    const { username, password } = this.form.value;
    this.auth.login(username, password).subscribe({
      next: user => {
        this.loading = false;
        if (user.mustChangePassword) {
          this.mustChangePassword = true;
          return;
        }
        this.router.navigateByUrl(user.roles.includes('admin') ? '/admin' : '/mobile');
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Credenziali non valide', 'Chiudi', { duration: 3000 });
      }
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { password, confirmPassword } = this.passwordForm.value;
    if (password !== confirmPassword) {
      this.snackBar.open('Le password non coincidono', 'Chiudi', { duration: 3000 });
      return;
    }
    this.loading = true;
    this.auth.changePassword(password).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigateByUrl('/mobile');
      },
      error: err => {
        this.loading = false;
        this.snackBar.open(err?.error?.message || err?.error?.error || 'Cambio password non riuscito', 'Chiudi', { duration: 4000 });
      }
    });
  }
}
