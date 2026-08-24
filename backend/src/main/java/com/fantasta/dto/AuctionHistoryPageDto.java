package com.fantasta.dto;

import java.util.List;

public record AuctionHistoryPageDto(List<AuctionHistoryDto> items, long total, int page, int size) {}
