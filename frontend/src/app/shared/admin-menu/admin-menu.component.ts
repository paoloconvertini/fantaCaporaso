import { Component, ViewChild } from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';

@Component({
  selector: 'app-admin-menu',
  templateUrl: './admin-menu.component.html',
  styleUrls: ['./admin-menu.component.css']
})
export class AdminMenuComponent {
  @ViewChild('menu') menu!: MatSidenav;

  toggleMenu() {
    if (this.menu) {
      this.menu.toggle();
    }
  }
}
