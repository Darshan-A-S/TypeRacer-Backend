package com.example.typeracer.dtos;

public record PlayerGameResult(
        String username,
        double wpm,
        double accuracy,
        int correctChars,
        int errors,
        int rank,
        boolean won
) {
}
