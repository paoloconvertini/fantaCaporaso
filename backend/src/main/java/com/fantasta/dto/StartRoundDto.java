package com.fantasta.dto;

public class StartRoundDto {
    public String player;
    // compatibilità doppia:
    public String team;            // usato dal BE attuale
    public String playerTeam;      // alias accettato

    public String role;            // usato dal BE attuale
    public String playerRole;      // alias accettato

    public Integer duration;       // usato dal BE attuale
    public Integer durationSeconds;// alias accettato

    public String tieBreak;
}
