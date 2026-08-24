package com.fantasta.dto;

import java.util.ArrayList;
import java.util.List;

public class NewspaperEditionDto {
    public String matchday;
    public String kicker;
    public String headline;
    public String standfirst;
    public String leadTitle;
    public String leadText;
    public String fantasfigaTitle;
    public String fantasfigaText;
    public List<NewspaperBriefDto> briefs = new ArrayList<>();
    public List<NewspaperMatchDto> matches = new ArrayList<>();
    public List<NewspaperStandingDto> standings = new ArrayList<>();
}
