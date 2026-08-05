package com.example.typeracer.service;

import com.example.typeracer.dtos.*;
import com.example.typeracer.entities.Game;
import com.example.typeracer.entities.GameResult;
import com.example.typeracer.entities.GameRoom;
import com.example.typeracer.entities.Player;
import com.example.typeracer.enums.GameState;
import com.example.typeracer.repository.GameRepository;
import com.example.typeracer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final int MAX_PLAYERS_PER_ROOM = 4;
    private static final int COUNTDOWN_SECONDS = 3;
    private static final long GAME_TIMEOUT_SECONDS = 300;
    private static final long ROOM_CLEANUP_SECONDS = 60;

    // unambiguous room-code charset (no I/0/1)
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final List<String> TEXT_BANK = List.of(
            "the quick brown fox jumps over the lazy dog",
            "practice makes perfect when you keep at it daily",
            "spring boot makes building web applications easier",
            "a smooth sea never made a skilled sailor in any port",
            "every expert was once a beginner who refused to quit",
            "the fastest way to get better is to race again",
            "focus on your own screen and ignore the noise around",
            "consistent practice beats talent when talent stops working",
            "small daily improvements compound into big results over time",
            "keep your fingers light and let the rhythm carry you forward"
    );

    private record SessionInfo(String roomId, String username) {
    }

    // ---------- 1. Join or create room ----------

    public GameRoom joinOrCreateRoom(String roomId, String username, Long userId, String sessionId) {
        if (roomId == null || roomId.isBlank()) {
            roomId = createRoomCode();
        }
        GameRoom room = rooms.computeIfAbsent(roomId, id -> {
            GameRoom r = new GameRoom();
            r.setId(id);
            r.setPlayers(new CopyOnWriteArrayList<>());
            r.setState(GameState.WAITING);
            return r;
        });

        synchronized (room) {
            if (room.getState() != GameState.WAITING) {
                throw new IllegalStateException("Room already in progress or finished");
            }
            boolean alreadyJoined = room.getPlayers().stream().anyMatch(p -> userId.equals(p.getUserId()));
            if (alreadyJoined) {
                throw new IllegalStateException("You are already in this room");
            }
            if (room.getPlayers().size() >= MAX_PLAYERS_PER_ROOM) {
                throw new IllegalStateException("Room is full");
            }

            Player player = new Player();
            player.setId(UUID.randomUUID().toString());
            player.setName(username);
            player.setUserId(userId);
            player.setCharIndex(0);
            player.setCorrectChars(0);
            player.setWpm(0.0);
            player.setAccuracy(0.0);
            player.setErrors(0);
            player.setReady(false);
            player.setHost(room.getPlayers().isEmpty());
            room.getPlayers().add(player);
            sessions.put(sessionId, new SessionInfo(room.getId(), username));

            List<PlayerStatus> statuses = playerStatuses(room);
            sendToUser(username, "/queue/room-joined", new RoomJoinedMessage(room.getId(), player.getId(), statuses));
            broadcastPlayersUpdate(room);
            return room;
        }
    }

    // ---------- 2. Ready toggle ----------

    public void setReady(String roomId, String username, boolean ready) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        synchronized (room) {
            Player player = findPlayerByUsername(room, username);
            if (player == null) return;
            player.setReady(ready);
            broadcastPlayersUpdate(room);
        }
    }

    // ---------- 3. Host starts the game ----------

    public void startGame(String roomId, String username) {
        GameRoom room = rooms.get(roomId);
        if (room == null) throw new IllegalStateException("Room not found");

        synchronized (room) {
            Player requester = findPlayerByUsername(room, username);
            if (requester == null || !requester.isHost()) {
                throw new IllegalStateException("Only the host can start the game");
            }
            if (room.getPlayers().size() < 2) {
                throw new IllegalStateException("Need at least 2 players to start");
            }
            boolean allReady = room.getPlayers().stream().allMatch(Player::isReady);
            if (!allReady) {
                throw new IllegalStateException("Not all players are ready");
            }

            String text = TEXT_BANK.get(ThreadLocalRandom.current().nextInt(TEXT_BANK.size()));
            room.setTargetText(text);
            room.setState(GameState.COUNTDOWN);

            List<String> playerNames = room.getPlayers().stream().map(Player::getName).toList();
            messagingTemplate.convertAndSend("/topic/room/" + room.getId(), new RoomReadyMessage(room.getId(), text, playerNames));
        }

        startCountdown(room);
    }

    // ---------- 4. Countdown ----------

    private void startCountdown(GameRoom room) {
        scheduler.schedule(() -> countdownTick(room, COUNTDOWN_SECONDS), 0, TimeUnit.MILLISECONDS);
    }

    private void countdownTick(GameRoom room, int remaining) {
        if (rooms.get(room.getId()) != room) return; // room cleaned up meanwhile
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), new CountdownMessage(remaining));

        if (remaining > 0) {
            scheduler.schedule(() -> countdownTick(room, remaining - 1), 1, TimeUnit.SECONDS);
            return;
        }

        synchronized (room) {
            if (room.getState() != GameState.COUNTDOWN) return;
            room.setState(GameState.IN_PROGRESS);
            room.setStartTime(LocalDateTime.now());
            scheduler.schedule(() -> forceEnd(room), GAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    // ---------- 5. Progress updates ----------

    public void updateProgress(String roomId, String username, Integer charIndex, Integer correctChars, Integer errors) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        boolean finished;
        String playerId;
        synchronized (room) {
            if (room.getState() != GameState.IN_PROGRESS) return;
            Player player = findPlayerByUsername(room, username);
            if (player == null) return;

            int typed = Math.max(0, charIndex == null ? 0 : charIndex);
            int correct = Math.max(0, correctChars == null ? 0 : correctChars);
            player.setCharIndex(typed);
            player.setCorrectChars(correct);
            player.setErrors(Math.max(0, errors == null ? 0 : errors));

            double minutesElapsed = Duration.between(room.getStartTime(), LocalDateTime.now()).toMillis() / 60000.0;
            player.setWpm(minutesElapsed > 0 ? (correct / 5.0) / minutesElapsed : 0.0);
            int totalTyped = correct + player.getErrors();
            player.setAccuracy(totalTyped > 0 ? correct * 100.0 / totalTyped : 0.0);

            messagingTemplate.convertAndSend("/topic/room/" + roomId,
                    new ProgressUpdateMessage(player.getId(), player.getName(), typed, correct, player.getWpm()));

            finished = typed >= room.getTargetText().length();
            playerId = player.getId();
        }

        if (finished) {
            endGame(room, playerId);
        }
    }

    // ---------- 6. End game ----------

    private void forceEnd(GameRoom room) {
        endGame(room, null);
    }

    private void endGame(GameRoom room, String winnerId) {
        synchronized (room) {
            if (room.getState() != GameState.IN_PROGRESS) return;
            room.setState(GameState.FINISHED);
            room.setFinishedAt(LocalDateTime.now());
        }

        List<Player> players = room.getPlayers();
        Player winner = players.stream().filter(p -> p.getId().equals(winnerId)).findFirst()
                .orElse(players.stream().max(Comparator.comparingDouble(Player::getWpm)).orElse(null));

        List<PlayerResult> results = new ArrayList<>();
        List<Player> sorted = players.stream()
                .sorted(Comparator.comparingDouble(Player::getWpm).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            p.setRank(i + 1);
            results.add(new PlayerResult(p.getId(), p.getName(), p.getWpm(), p.getCorrectChars(), p.getAccuracy(), p.getErrors()));
        }

        messagingTemplate.convertAndSend("/topic/room/" + room.getId(),
                new GameOverMessage(winner != null ? winner.getId() : null, results));

        persistGame(room, winner);

        scheduler.schedule(() -> rooms.remove(room.getId()), ROOM_CLEANUP_SECONDS, TimeUnit.SECONDS);
    }

    private void persistGame(GameRoom room, Player winner) {
        Game game = new Game();
        game.setRoomCode(room.getId());
        game.setTargetText(room.getTargetText());
        game.setStartedAt(room.getStartTime());
        game.setFinishedAt(room.getFinishedAt());
        game.setWinnerUserId(winner != null ? winner.getUserId() : null);

        List<GameResult> results = room.getPlayers().stream()
                .map(p -> GameResult.builder()
                        .game(game)
                        .user(userRepository.getReferenceById(p.getUserId()))
                        .username(p.getName())
                        .wpm(p.getWpm())
                        .accuracy(p.getAccuracy())
                        .correctChars(p.getCorrectChars())
                        .errors(p.getErrors())
                        .rankNo(p.getRank())
                        .won(winner != null && winner.getId().equals(p.getId()))
                        .build())
                .toList();
        game.setResults(results);
        gameRepository.save(game);
    }

    // ---------- 7. Disconnect handling ----------

    public void handleDisconnect(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        if (info == null) return;

        GameRoom room = rooms.get(info.roomId());
        if (room == null) return;

        synchronized (room) {
            room.getPlayers().removeIf(p -> info.username().equals(p.getName()));
            if (room.getPlayers().isEmpty()) {
                rooms.remove(room.getId());
                return;
            }
            if (room.getState() == GameState.WAITING) {
                promoteHost(room);
                broadcastPlayersUpdate(room);
            }
        }
    }

    private void promoteHost(GameRoom room) {
        boolean hasHost = room.getPlayers().stream().anyMatch(Player::isHost);
        if (!hasHost && !room.getPlayers().isEmpty()) {
            room.getPlayers().get(0).setHost(true);
        }
    }

    // ---------- helpers ----------

    private String createRoomCode() {
        StringBuilder sb = new StringBuilder(6);
        String code;
        do {
            sb.setLength(0);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(ThreadLocalRandom.current().nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }

    private List<PlayerStatus> playerStatuses(GameRoom room) {
        return room.getPlayers().stream()
                .map(p -> new PlayerStatus(p.getId(), p.getName(), p.isReady(), p.isHost()))
                .toList();
    }

    private void broadcastPlayersUpdate(GameRoom room) {
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(),
                new PlayersUpdateMessage(room.getId(), playerStatuses(room)));
    }

    private Player findPlayerByUsername(GameRoom room, String username) {
        return room.getPlayers().stream()
                .filter(p -> username.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    private void sendToUser(String username, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(username, destination, payload);
    }
}
