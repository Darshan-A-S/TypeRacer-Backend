package com.example.typeracer.controllers;

import com.example.typeracer.dtos.*;
import com.example.typeracer.services.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final RoomService roomService;

    @MessageMapping("/join")
    public void joinRoom(JoinRoomMessage message, SimpMessageHeaderAccessor headerAccessor) {
        roomService.joinOrCreateRoom(message.getRoomId(), message.getPlayerName(), headerAccessor.getSessionId());
    }

    @MessageMapping("/ready")
    public void ready(ReadyMessage message) {
        roomService.setReady(message.getRoomId(), message.getPlayerId(), message.isReady());
    }

    @MessageMapping("/start")
    public void start(StartGameMessage message) {
        roomService.startGame(message.getRoomId(), message.getPlayerId());
    }

    @MessageMapping("/progress")
    public void progress(ProgressMessage message) {
        roomService.updateProgress(
                message.getRoomId(),
                message.getPlayerId(),
                message.getCharIndex(),
                message.getCorrectChars()
        );
    }
}