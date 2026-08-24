package com.fantasta.dto;

import java.util.ArrayList;
import java.util.List;

public class MarketPlayerImportResult {
    public boolean preview;
    public int total;
    public List<MarketPlayerChangeDto> updated = new ArrayList<>();
    public List<MarketPlayerChangeDto> newPlayers = new ArrayList<>();
    public List<MarketPlayerChangeDto> departedInRosters = new ArrayList<>();
    public List<MarketPlayerChangeDto> departedFree = new ArrayList<>();

    public MarketPlayerImportResult(boolean preview, int total) {
        this.preview = preview;
        this.total = total;
    }
}
