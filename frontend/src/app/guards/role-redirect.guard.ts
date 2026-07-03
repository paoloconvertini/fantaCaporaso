import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { KeycloakService } from '../services/keycloak.service';

@Injectable({
  providedIn: 'root'
})
export class AdminOnlyGuard implements CanActivate {

  constructor(private keycloak: KeycloakService, private router: Router) {}

  canActivate(): boolean | UrlTree {
    return this.keycloak.isAdmin ? true : this.router.parseUrl('/mobile');
  }
}

@Injectable({
  providedIn: 'root'
})
export class UserOnlyGuard implements CanActivate {

  constructor(private keycloak: KeycloakService, private router: Router) {}

  canActivate(): boolean | UrlTree {
    return this.keycloak.isAdmin ? this.router.parseUrl('/admin') : true;
  }
}
