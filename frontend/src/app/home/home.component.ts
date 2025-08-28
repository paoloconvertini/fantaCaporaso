import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
    selector: 'app-home',
    templateUrl: './home.component.html',
    styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
    pid: number | null = null;
    participants: any[] = [];

    constructor(private router: Router, private http: HttpClient) {}

    ngOnInit() {
        this.http.get<any[]>('/api/participant/all').subscribe({
            next: res => this.participants = res,
            error: () => console.error('Errore caricamento partecipanti')
        });
    }

    goAdmin() {
        this.router.navigate(['/admin']);
    }

    goMobile() {
        if (this.pid) {
            this.router.navigate(['/mobile'], { queryParams: { pid: this.pid } });
        } else {
            alert('Seleziona un partecipante');
        }
    }
}
