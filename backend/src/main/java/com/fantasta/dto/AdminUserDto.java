package com.fantasta.dto;

public class AdminUserDto {
    public String username;
    public String role;
    public Long participantId;
    public String participantName;
    public boolean mustChangePassword;

    public AdminUserDto(String username, String role, Long participantId,
                        String participantName, boolean mustChangePassword) {
        this.username = username;
        this.role = role;
        this.participantId = participantId;
        this.participantName = participantName;
        this.mustChangePassword = mustChangePassword;
    }
}
