package com.fantasta.model;

import java.util.*;

public class RoundState {
    public String roundId;
    public String player;
    public String playerTeam;
    public String playerRole;
    public Integer value;             // valore calciatore (Excel colonna E)
    public boolean closed = false;
    public Integer durationSeconds;
    public Long endEpochMillis;

    public Map<String, Double> bids = new LinkedHashMap<>();
    public Winner winner;
    public List<Long> tieUsers;       // lista ID partecipanti in parità
    public String tieBreak;           // NONE | FIRST | RANDOM
    public Set<Long> allowedUsers;
}
