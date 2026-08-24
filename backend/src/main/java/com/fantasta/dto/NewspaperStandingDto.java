package com.fantasta.dto;

public class NewspaperStandingDto {
    public int position;
    public String team;
    public String record;
    public String points;

    public NewspaperStandingDto(int position, String team, String record, String points) {
        this.position = position;
        this.team = team;
        this.record = record;
        this.points = points;
    }
}
