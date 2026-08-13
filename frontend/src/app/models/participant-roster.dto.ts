import { RosterDto } from './roster.dto';

export interface ParticipantRosterDto {
    participantId: number;
    participantName: string;
    username?: string;
    roster: RosterDto[];
}
