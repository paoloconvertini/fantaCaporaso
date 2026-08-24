package com.fantasta.dto;

import java.util.ArrayList;
import java.util.List;

public class NewspaperPreviewDto {
    public String competition;
    public boolean calculated;
    public int teamsFound;
    public String message;
    public List<NewspaperMatchDto> matches = new ArrayList<>();
    public List<NewspaperStandingDto> standings = new ArrayList<>();
    public List<NewspaperTeamSheetDto> teamSheets = new ArrayList<>();
    public List<NewspaperFantasfigaDto> fantasfiga = new ArrayList<>();
}
