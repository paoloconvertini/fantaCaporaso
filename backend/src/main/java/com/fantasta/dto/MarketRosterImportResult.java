package com.fantasta.dto;

import java.util.ArrayList;
import java.util.List;

public class MarketRosterImportResult {
    public boolean preview;
    public List<String> unchanged = new ArrayList<>();
    public List<String> exchanges = new ArrayList<>();
    public List<String> releases = new ArrayList<>();
    public List<String> goalkeeperCorrections = new ArrayList<>();
    public List<String> rosterCorrections = new ArrayList<>();
    public List<String> errors = new ArrayList<>();

    public MarketRosterImportResult(boolean preview) {
        this.preview = preview;
    }
}
