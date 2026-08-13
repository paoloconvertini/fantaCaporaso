import { Injectable } from '@angular/core';
import {
    HttpEvent,
    HttpInterceptor,
    HttpHandler,
    HttpRequest,
    HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
    constructor(private snackBar: MatSnackBar, private auth: AuthService, private router: Router) {}

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(req).pipe(
            catchError((err: HttpErrorResponse) => {
                // 🔹 Gestione errori noti
                let msg = 'Si è verificato un errore';

                if (err.status === 0) {
                    msg = 'Server non raggiungibile';
                } else if (err.status === 401) {
                    msg = 'Sessione scaduta — effettua di nuovo il login';
                } else if (err.status === 403) {
                    msg = err.error?.message || 'Operazione non consentita';
                } else if (err.status === 404) {
                    msg = 'Risorsa non trovata';
                } else if (err.status === 400) {
                    msg = err.error?.message || 'Richiesta non valida';
                } else if (err.status >= 500) {
                    msg = 'Errore interno del server';
                } else if (err.error?.message) {
                    msg = err.error.message;
                }

                // 🔸 Mostra snackbar globale
                this.snackBar.open(msg, 'Chiudi', {
                    duration: 4000,
                    panelClass: ['snackbar-error']
                });

                if (err.status === 401 && !req.url.includes('/api/auth/login')) {
                    this.router.navigateByUrl('/login');
                }

                return throwError(() => err);
            })
        );
    }
}
