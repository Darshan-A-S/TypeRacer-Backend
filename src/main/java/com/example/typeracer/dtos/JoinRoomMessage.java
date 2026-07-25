package com.example.typeracer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinRoomMessage {
    private String roomId;       // null/empty = create a new room
    private String playerName;
}
