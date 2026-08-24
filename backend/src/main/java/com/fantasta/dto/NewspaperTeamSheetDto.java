package com.fantasta.dto;

import java.util.ArrayList;
import java.util.List;

public class NewspaperTeamSheetDto {
    public String team;
    public List<NewspaperPlayerDto> players = new ArrayList<>();

    public NewspaperTeamSheetDto(String team) {
        this.team = team;
    }
}
