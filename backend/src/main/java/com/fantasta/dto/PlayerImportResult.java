package com.fantasta.dto;

public class PlayerImportResult {
    public int inserted;
    public int updated;
    public int reactivated;
    public int softDeleted;
    public int unassigned;

    public PlayerImportResult(int inserted, int updated, int reactivated, int softDeleted, int unassigned) {
        this.inserted = inserted;
        this.updated = updated;
        this.reactivated = reactivated;
        this.softDeleted = softDeleted;
        this.unassigned = unassigned;
    }
}
