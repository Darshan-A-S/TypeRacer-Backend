package com.example.typeracer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerStatus {
    private String playerId;
    private String name;
    private boolean ready;
    private boolean host;
}