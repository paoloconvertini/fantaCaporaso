import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RosterDto } from '../models/roster.dto';
import { ParticipantRosterDto } from '../models/participant-roster.dto';

@Injectable({ providedIn: 'root' })
export class RosterService {
    private readonly BASE_URL = '/api/admin/rosters';

    constructor(private http: HttpClient) {}

    getMyRoster(participantId?: number): Observable<RosterDto[]> {
        const url = participantId
            ? `${this.BASE_URL}/mine?participantId=${participantId}`
            : `${this.BASE_URL}/mine`;
        return this.http.get<RosterDto[]>(url);
    }

    getGroupedRosters(): Observable<ParticipantRosterDto[]> {
        return this.http.get<ParticipantRosterDto[]>(`${this.BASE_URL}/grouped`);
    }

    svincola(participantId: number, playerId: number): Observable<void> {
        return this.http.post<void>(
            `${this.BASE_URL}/svincola?participantId=${participantId}`,
            { playerId }
        );
    }
}
