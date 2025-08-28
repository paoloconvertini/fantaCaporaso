package com.fantasta.model;

import java.util.*;

public class RoundState {
    public String roundId;
    public String player;
    public String playerTeam;
    public String playerRole;
    public boolean closed;
    public java.util.Map<String, Integer> bids = new java.util.LinkedHashMap<>();
    public Winner winner;
    public Integer durationSeconds;
    public Long endEpochMillis;
    public java.util.List<String> allowedUsers;
    public List<Long> tieUsers;


}
