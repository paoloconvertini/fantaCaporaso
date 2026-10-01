package com.fantasta.service;

/** Hypothetical purchases at the smallest bid strictly above the user's maximum. */
public final class TargetCompetitionCalculator {
    private TargetCompetitionCalculator() {}

    public static int capacity(int credits, int roleSlots, int totalSlots, int purchaseSize, int ownMaximum) {
        if (purchaseSize < 1 || ownMaximum < 1 || roleSlots < purchaseSize) return 0;
        int maximumPurchases = roleSlots / purchaseSize;
        int capacity = 0;
        for (int purchases = 1; purchases <= maximumPurchases; purchases++) {
            long bids = (long) purchases * (ownMaximum + 1L);
            int reserve = Math.max(0, totalSlots - purchases * purchaseSize);
            if (bids + reserve > credits) break;
            capacity = purchases;
        }
        return capacity;
    }
}
