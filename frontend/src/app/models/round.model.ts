export interface Round {
    player: string;
    playerTeam?: string;
    playerRole: string;
    value?: number;
    durationSeconds: number;
    tieBreak?: string;
    allowedUsers?: number[];
}
