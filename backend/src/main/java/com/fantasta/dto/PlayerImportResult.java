package com.fantasta.dto;

public class PlayerImportResult {
    public int total;
    public int inserted;
    public int updated;
    public int reactivated;
    public int softDeleted;
    public int unassigned;
    public boolean preview;

    public PlayerImportResult(int inserted, int updated, int reactivated, int softDeleted, int unassigned) {
        this(inserted, inserted, updated, reactivated, softDeleted, unassigned, false);
    }

    public PlayerImportResult(int total, int inserted, int updated, int reactivated,
                              int softDeleted, int unassigned, boolean preview) {
        this.total = total;
        this.inserted = inserted;
        this.updated = updated;
        this.reactivated = reactivated;
        this.softDeleted = softDeleted;
        this.unassigned = unassigned;
        this.preview = preview;
    }
}
