package com.fantasta.dto;

public class NewspaperPlayerDto {
    public String name;
    public String role;
    public String vote;
    public String fantasyVote;
    public boolean starter;
    public boolean counted;

    public NewspaperPlayerDto(String name, String role, String vote, String fantasyVote, boolean starter, boolean counted) {
        this.name = name;
        this.role = role;
        this.vote = vote;
        this.fantasyVote = fantasyVote;
        this.starter = starter;
        this.counted = counted;
    }
}
