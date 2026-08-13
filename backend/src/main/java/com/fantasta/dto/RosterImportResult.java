package com.fantasta.dto;

import java.util.List;

public class RosterImportResult {
    public int inserted;
    public List<String> skipped;
    public int teamsFound;
    public int teamsCreated;
    public boolean preview;

    public RosterImportResult(int inserted, List<String> skipped) {
        this.inserted = inserted;
        this.skipped = skipped;
    }

    public RosterImportResult(int inserted, List<String> skipped, int teamsFound, int teamsCreated, boolean preview) {
        this(inserted, skipped);
        this.teamsFound = teamsFound;
        this.teamsCreated = teamsCreated;
        this.preview = preview;
    }
}
