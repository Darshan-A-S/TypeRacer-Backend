package com.example.typeracer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerResult {
    private String playerId;
    private String playerName;
    private Double wpm;
    private Integer correctChars;
}