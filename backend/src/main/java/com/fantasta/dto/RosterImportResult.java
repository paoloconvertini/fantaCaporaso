package com.fantasta.dto;

import java.util.List;

public class RosterImportResult {
    public int inserted;
    public List<String> skipped;

    public RosterImportResult(int inserted, List<String> skipped) {
        this.inserted = inserted;
        this.skipped = skipped;
    }
}
