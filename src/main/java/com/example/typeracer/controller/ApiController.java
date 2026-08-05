package com.example.typeracer.controller;

import com.example.typeracer.config.UserPrincipal;
import com.example.typeracer.dtos.GameHistoryResponse;
import com.example.typeracer.dtos.ProfileResponse;
import com.example.typeracer.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class ApiController {

    private final StatsService statsService;

    @GetMapping
    public ProfileResponse profile(Authentication authentication) {
        return statsService.getProfile(userId(authentication));
    }

    @GetMapping("/games")
    public List<GameHistoryResponse> games(Authentication authentication) {
        return statsService.getGameHistory(userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getUserId();
    }
}
