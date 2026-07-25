package com.example.typeracer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadyMessage {
    private String roomId;
    private String playerId;
    private boolean ready;
}