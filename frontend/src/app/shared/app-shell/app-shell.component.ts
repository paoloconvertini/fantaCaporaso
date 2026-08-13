import {Component, HostListener, OnInit, ViewChild} from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';
import {AuthService} from "../../services/auth.service";

@Component({
  selector: 'app-shell',
  templateUrl: './app-shell.component.html',
  styleUrls: ['./app-shell.component.css']
})
export class AppShellComponent implements OnInit {
  @ViewChild('menu') menu!: MatSidenav;

  summaryOpen = false;
  isAdmin = false;
  isParticipant = false;
  menuOpened = window.innerWidth >= 1024;
  menuMode: 'side' | 'over' = this.menuOpened ? 'side' : 'over';

  constructor(private auth: AuthService) {}

  async ngOnInit() {
    const roles = this.auth.roles;
    this.isAdmin = roles.includes('admin');
    this.isParticipant = roles.includes('user');
  }

  toggleMenu() {
    if (this.menu) {
      this.menu.toggle();
    }
  }

  closeMenuOnMobile() {
    if (this.menuMode === 'over') this.menu?.close();
  }

  @HostListener('window:resize')
  onResize() {
    const desktop = window.innerWidth >= 1024;
    this.menuMode = desktop ? 'side' : 'over';
    this.menuOpened = desktop;
    if (this.menu) desktop ? this.menu.open() : this.menu.close();
  }
}
