package com.example.typeracer.dtos;

public record ProfileResponse(
        Long userId,
        String username,
        String email,
        long gamesPlayed,
        long gamesWon,
        double avgWpm,
        double bestWpm,
        double avgAccuracy
) {
}
