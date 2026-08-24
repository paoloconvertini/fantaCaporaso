package com.fantasta.dto;

import java.util.List;

public record RosterSwapResult(List<String> movedToDestination,
                               List<String> movedToSource,
                               int sourceRemainingCredits,
                               int destinationRemainingCredits) {}
