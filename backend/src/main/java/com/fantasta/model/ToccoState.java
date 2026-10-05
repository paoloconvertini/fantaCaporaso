package com.fantasta.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToccoState {
    public String id;
    public List<Long> order = new ArrayList<>();
    public Long firstParticipantId;
    public Map<String, Integer> choices = new LinkedHashMap<>();
    public Integer sum;
    public Long winnerParticipantId;
}
