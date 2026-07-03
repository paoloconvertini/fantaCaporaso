import { RosterDto } from './roster.dto';

export interface ParticipantRosterDto {
    participantId: number;
    participantName: string;
    roster: RosterDto[];
}
