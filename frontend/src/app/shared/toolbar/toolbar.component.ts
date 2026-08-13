import { Component, OnInit } from '@angular/core';
import { EventEmitter, Output } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.css']
})
export class ToolbarComponent implements OnInit {
  username: string = '';
  @Output() menuToggle = new EventEmitter<void>();

  constructor(private auth: AuthService) {}

  ngOnInit(): void {
    this.username = this.auth.username;
  }

  logout(): void {
    this.auth.logout();
  }
}
