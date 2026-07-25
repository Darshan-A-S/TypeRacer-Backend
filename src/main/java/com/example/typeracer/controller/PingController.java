package com.example.typeracer.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class PingController {

    @MessageMapping("/ping")   // client sends to /app/ping
    @SendTo("/topic/pong")     // broadcasts to /topic/pong
    public String handlePing(String message) {
        return "pong: " + message;
    }

}
