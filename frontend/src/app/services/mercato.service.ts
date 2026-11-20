import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MercatoConfigDto } from '../models/mercato-config.dto';

@Injectable({ providedIn: 'root' })
export class MercatoService {
    private readonly BASE_URL = '/api/mercato/config';

    constructor(private http: HttpClient) {}

    getConfig(): Observable<MercatoConfigDto> {
        return this.http.get<MercatoConfigDto>(this.BASE_URL);
    }

    confirmNoDepartures(sessionCode: string): Observable<MercatoConfigDto> {
        return this.http.post<MercatoConfigDto>('/api/mercato/departures/none', { sessionCode });
    }

    updateConfig(dto: MercatoConfigDto): Observable<MercatoConfigDto> {
        return this.http.post<MercatoConfigDto>(this.BASE_URL, dto);
    }
}

