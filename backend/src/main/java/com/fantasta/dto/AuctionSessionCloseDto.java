package com.fantasta.dto;

public record AuctionSessionCloseDto(Long id, String sessionCode, String label, String publishStatus,
                                     boolean alreadyClosed) {
}
