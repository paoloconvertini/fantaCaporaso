package com.fantasta.dto;

import java.util.List;

public class ReleaseResultDto {
    public List<String> players;
    public double refundedCredits;
    public int remainingCredits;
    public boolean departed;
    public boolean goalkeeperPackage;

    public ReleaseResultDto(List<String> players, double refundedCredits, int remainingCredits,
                            boolean departed, boolean goalkeeperPackage) {
        this.players = players;
        this.refundedCredits = refundedCredits;
        this.remainingCredits = remainingCredits;
        this.departed = departed;
        this.goalkeeperPackage = goalkeeperPackage;
    }
}
