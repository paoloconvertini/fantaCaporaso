export interface Round {
    player: string;
    playerTeam?: string;
    playerRole: string;
    value?: number;
    minimumBid?: number;
    durationSeconds: number;
    tieBreak?: string;
    allowedUsers?: number[];
    previousAssignment?: { player: string; playerTeam?: string; playerRole?: string; winner: string; amount: number };
}
