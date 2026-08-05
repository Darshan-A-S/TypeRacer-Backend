package com.example.typeracer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgressUpdateMessage {
    private String playerId;
    private String playerName;
    private Integer charIndex;
    private Integer correctChars;
    private Double wpm;
}