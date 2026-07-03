package com.fantasta.dto;

import java.time.LocalDateTime;

public class ErrorResponse {
    public String code;
    public String message;
    public LocalDateTime timestamp = LocalDateTime.now();

    public ErrorResponse() {}

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}

