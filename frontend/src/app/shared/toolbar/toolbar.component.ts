import { Component } from '@angular/core';
import { EventEmitter, Output } from '@angular/core';
import { KeycloakService } from '../../services/keycloak.service';

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.css']
})
export class ToolbarComponent {
  username: string = '';
  @Output() menuToggle = new EventEmitter<void>();

  constructor(private kc: KeycloakService) {
    this.username = this.kc.getUsername();
  }

  logout(): void {
    this.kc.logout();
  }
}
