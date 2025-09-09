package com.fantasta.dto;

public class PlayerDto {
    public Long id;
    public String name;
    public String team;
    public String role;
    public Double valore;

    public PlayerDto(Long id, String name, String team, String role, Double valore) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.role = role;
        this.valore = valore;
    }
}
