import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(private auth: AuthService, private router: Router) {}

  canActivate(): boolean | UrlTree {
    if (this.auth.user?.mustChangePassword) {
      return this.router.parseUrl('/login');
    }
    return this.auth.isAuthenticated ? true : this.router.parseUrl('/login');
  }
}

@Injectable({
  providedIn: 'root'
})
export class AdminOnlyGuard implements CanActivate {

  constructor(private auth: AuthService, private router: Router) {}

  canActivate(): boolean | UrlTree {
    if (!this.auth.isAuthenticated) {
      return this.router.parseUrl('/login');
    }
    return this.auth.isAdmin ? true : this.router.parseUrl('/mobile');
  }
}

@Injectable({
  providedIn: 'root'
})
export class UserOnlyGuard implements CanActivate {

  constructor(private auth: AuthService, private router: Router) {}

  canActivate(): boolean | UrlTree {
    if (!this.auth.isAuthenticated) {
      return this.router.parseUrl('/login');
    }
    return this.auth.isAdmin ? this.router.parseUrl('/admin') : true;
  }
}
