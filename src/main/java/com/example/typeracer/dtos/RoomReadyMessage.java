package com.example.typeracer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomReadyMessage {
    private String roomId;
    private String targetText;
    private List<String> playerNames;
}