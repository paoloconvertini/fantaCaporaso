package com.fantasta.dto;

import java.util.ArrayList;
import java.util.List;

public class MarketDepartureImportResult {
    public boolean preview;
    public List<MarketPlayerChangeDto> players = new ArrayList<>();
    public List<String> errors = new ArrayList<>();

    public MarketDepartureImportResult(boolean preview) {
        this.preview = preview;
    }
}
