package com.fantasta.dto;

public class NewspaperMatchDto {
    public String home;
    public String away;
    public String result;
    public String goals;

    public NewspaperMatchDto(String home, String away, String result) {
        this.home = home;
        this.away = away;
        this.result = result;
    }
}
