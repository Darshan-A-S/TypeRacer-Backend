package com.example.typeracer.entities;

import com.example.typeracer.enums.GameState;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GameRoom {

    private String id;
    private List<Player> players;
    private GameState state;
    private String targetText;
    private LocalDateTime startTime;
    private LocalDateTime finishedAt;
}