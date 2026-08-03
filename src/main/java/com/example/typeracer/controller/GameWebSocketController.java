package com.example.typeracer.controller;

import com.example.typeracer.config.UserPrincipal;
import com.example.typeracer.dtos.*;
import com.example.typeracer.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final RoomService roomService;

    @MessageMapping("/join")
    public void joinRoom(JoinRoomMessage message, Principal principal) {
        UserPrincipal user = (UserPrincipal) principal;
        roomService.joinOrCreateRoom(message.getRoomId(), user.getName(), user.getUserId());
    }

    @MessageMapping("/ready")
    public void ready(ReadyMessage message, Principal principal) {
        roomService.setReady(message.getRoomId(), principal.getName(), message.isReady());
    }

    @MessageMapping("/start")
    public void start(StartGameMessage message, Principal principal) {
        roomService.startGame(message.getRoomId(), principal.getName());
    }

    @MessageMapping("/progress")
    public void progress(ProgressMessage message, Principal principal) {
        roomService.updateProgress(
                message.getRoomId(),
                principal.getName(),
                message.getCharIndex(),
                message.getCorrectChars()
        );
    }
}
