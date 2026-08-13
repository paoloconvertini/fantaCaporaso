package com.fantasta.dto;

import java.util.List;

public class AuthUserDto {
    public String username;
    public List<String> roles;
    public Long participantId;
    public String participantName;
    public boolean mustChangePassword;

    public AuthUserDto(String username, List<String> roles, Long participantId, String participantName) {
        this(username, roles, participantId, participantName, false);
    }

    public AuthUserDto(String username, List<String> roles, Long participantId,
                       String participantName, boolean mustChangePassword) {
        this.username = username;
        this.roles = roles;
        this.participantId = participantId;
        this.participantName = participantName;
        this.mustChangePassword = mustChangePassword;
    }
}
