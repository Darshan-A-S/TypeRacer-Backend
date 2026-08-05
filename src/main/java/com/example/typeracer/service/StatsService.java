package com.example.typeracer.service;

import com.example.typeracer.dtos.GameHistoryResponse;
import com.example.typeracer.dtos.PlayerGameResult;
import com.example.typeracer.dtos.ProfileResponse;
import com.example.typeracer.entities.Game;
import com.example.typeracer.entities.GameResult;
import com.example.typeracer.entities.User;
import com.example.typeracer.repository.GameResultRepository;
import com.example.typeracer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final GameResultRepository gameResultRepository;

    public ProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        long games = gameResultRepository.countGames(userId);
        long wins = gameResultRepository.countWins(userId);
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                games,
                wins,
                round(avgOrZero(gameResultRepository.avgWpm(userId))),
                round(avgOrZero(gameResultRepository.bestWpm(userId))),
                round(avgOrZero(gameResultRepository.avgAccuracy(userId)))
        );
    }

    @Transactional(readOnly = true)
    public List<GameHistoryResponse> getGameHistory(Long userId) {
        return gameResultRepository.findRecentByUserId(userId, PageRequest.of(0, 10)).stream()
                .map(result -> toHistory(result.getGame()))
                .toList();
    }

    private GameHistoryResponse toHistory(Game game) {
        List<PlayerGameResult> results = game.getResults().stream()
                .sorted(Comparator.comparingInt(GameResult::getRankNo))
                .map(r -> new PlayerGameResult(
                        r.getUsername(), r.getWpm(), r.getAccuracy(),
                        r.getCorrectChars(), r.getErrors(), r.getRankNo(), r.isWon()))
                .toList();
        return new GameHistoryResponse(game.getId(), game.getRoomCode(), game.getFinishedAt(), results);
    }

    private static double avgOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
