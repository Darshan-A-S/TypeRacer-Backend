package com.example.typeracer.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record GameHistoryResponse(
        Long gameId,
        String roomCode,
        LocalDateTime finishedAt,
        List<PlayerGameResult> results
) {
}
