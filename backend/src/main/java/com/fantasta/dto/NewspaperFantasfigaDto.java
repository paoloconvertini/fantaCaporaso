package com.fantasta.dto;

public class NewspaperFantasfigaDto {
    public String team;
    public String score;
    public double actualPoints;
    public double expectedPoints;
    public double delta;
    public String verdict;

    public NewspaperFantasfigaDto(String team, String score, double actualPoints, double expectedPoints,
                                  double delta, String verdict) {
        this.team = team;
        this.score = score;
        this.actualPoints = actualPoints;
        this.expectedPoints = expectedPoints;
        this.delta = delta;
        this.verdict = verdict;
    }
}
