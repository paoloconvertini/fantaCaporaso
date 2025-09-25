import {Component, OnInit, ViewChild} from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';
import {KeycloakService} from "../../services/keycloak.service";

@Component({
  selector: 'app-shell',
  templateUrl: './app-shell.component.html',
  styleUrls: ['./app-shell.component.css']
})
export class AppShellComponent implements OnInit {
  @ViewChild('menu') menu!: MatSidenav;

  summaryOpen = false;
  isAdmin = false; // da valorizzare leggendo il ruolo da Keycloak

  constructor(private keycloak: KeycloakService) {}

  async ngOnInit() {
    const roles = this.keycloak.getRoles();
    this.isAdmin = roles.includes('admin');
  }

  toggleMenu() {
    if (this.menu) {
      this.menu.toggle();
    }
  }
}
