import { Component, OnInit } from '@angular/core';
import { EventEmitter, Output } from '@angular/core';
import { KeycloakService } from '../../services/keycloak.service';

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.css']
})
export class ToolbarComponent implements OnInit {
  username: string = '';
  @Output() menuToggle = new EventEmitter<void>();

  constructor(private kc: KeycloakService) {}

  ngOnInit(): void {
    this.username = this.kc.getUsername();
  }

  logout(): void {
    this.kc.logout();
  }
}
