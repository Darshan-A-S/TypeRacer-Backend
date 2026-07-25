package com.example.typeracer.services;

import com.example.typeracer.dtos.*;
import com.example.typeracer.entities.GameRoom;
import com.example.typeracer.entities.Player;
import com.example.typeracer.enums.GameState;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final int MAX_PLAYERS_PER_ROOM = 2;

    private static final List<String> TEXT_BANK = List.of(
            "the quick brown fox jumps over the lazy dog",
            "practice makes perfect when you keep at it daily",
            "spring boot makes building web applications easier"
    );

    // ---------- 1. Join or create room ----------

    public GameRoom joinOrCreateRoom(String roomId, String playerName, String sessionId) {
        GameRoom room;
        boolean isFirstPlayer = false;

        if (roomId == null || roomId.isBlank()) {
            roomId = UUID.randomUUID().toString().substring(0, 6);
            System.out.println("Created new room with ID: " + roomId);

            room = new GameRoom();
            room.setId(roomId);
            room.setPlayers(new ArrayList<>());
            room.setState(GameState.WAITING);
            rooms.put(roomId, room);
            isFirstPlayer = true;
        } else {
            room = rooms.get(roomId);
            if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);
            if (room.getState() != GameState.WAITING) throw new IllegalStateException("Room already in progress or finished");
            boolean alreadyJoined = room.getPlayers().stream()
                    .anyMatch(p -> sessionId.equals(p.getWebSessionId()));
            if (alreadyJoined) {
                throw new IllegalStateException("This session already joined this room");
            }
            if (room.getPlayers().size() >= MAX_PLAYERS_PER_ROOM) throw new IllegalStateException("Room is full");
        }

        Player player = new Player();
        player.setId(UUID.randomUUID().toString());
        player.setName(playerName);
        player.setCharIndex(0);
        player.setCorrectChars(0);
        player.setWpm(0.0);
        player.setReady(false);
        player.setHost(isFirstPlayer);
        player.setWebSessionId(sessionId);

        room.getPlayers().add(player);

        List<PlayerStatus> statuses = room.getPlayers().stream()
                .map(p -> new PlayerStatus(p.getId(), p.getName(), p.isReady(), p.isHost()))
                .toList();
        sendToSession(sessionId, "/queue/room-joined", new RoomJoinedMessage(room.getId(), player.getId(), statuses));

        broadcastPlayersUpdate(room);

        return room;
    }

    // ---------- 2. Ready toggle ----------

    public void setReady(String roomId, String playerId, boolean ready) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;
        Player player = findPlayer(room, playerId);
        if (player == null) return;

        player.setReady(ready);
        broadcastPlayersUpdate(room);
    }

    private void broadcastPlayersUpdate(GameRoom room) {
        List<PlayerStatus> statuses = room.getPlayers().stream()
                .map(p -> new PlayerStatus(p.getId(), p.getName(), p.isReady(), p.isHost()))
                .toList();
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), new PlayersUpdateMessage(room.getId(), statuses));
    }

    // ---------- 3. Host starts the game ----------

    public void startGame(String roomId, String requesterId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) throw new IllegalStateException("Room not found");

        Player requester = findPlayer(room, requesterId);
        if (requester == null || !requester.isHost()) {
            throw new IllegalStateException("Only the host can start the game");
        }
        if (room.getPlayers().size() < MAX_PLAYERS_PER_ROOM) {
            throw new IllegalStateException("Not enough players to start");
        }
        boolean allReady = room.getPlayers().stream().allMatch(Player::isReady);
        if (!allReady) {
            throw new IllegalStateException("Not all players are ready");
        }

        String text = TEXT_BANK.get(new Random().nextInt(TEXT_BANK.size()));
        room.setTargetText(text);
        room.setState(GameState.COUNTDOWN);

        List<String> playerNames = room.getPlayers().stream().map(Player::getName).toList();
        RoomReadyMessage msg = new RoomReadyMessage(room.getId(), text, playerNames);
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), msg);

        startCountdown(room);
    }

    // ---------- 4. Countdown ----------

    private void startCountdown(GameRoom room) {
        int[] secondsRemaining = {3};

        scheduler.scheduleAtFixedRate(() -> {
            if (secondsRemaining[0] > 0) {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + room.getId(),
                        new CountdownMessage(secondsRemaining[0])
                );
                secondsRemaining[0]--;
            } else {
                room.setState(GameState.IN_PROGRESS);
                room.setStartTime(LocalDateTime.now());
                messagingTemplate.convertAndSend(
                        "/topic/room/" + room.getId(),
                        new CountdownMessage(0)
                );
                throw new RuntimeException("stop");
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    // ---------- 5. Progress updates ----------

    public void updateProgress(String roomId, String playerId, Integer charIndex, Integer correctChars) {
        GameRoom room = rooms.get(roomId);
        if (room == null || room.getState() != GameState.IN_PROGRESS) return;

        Player player = findPlayer(room, playerId);
        if (player == null) return;

        player.setCharIndex(charIndex);
        player.setCorrectChars(correctChars);

        double minutesElapsed = Duration.between(room.getStartTime(), LocalDateTime.now()).toMillis() / 60000.0;
        double wpm = minutesElapsed > 0 ? (correctChars / 5.0) / minutesElapsed : 0.0;
        player.setWpm(wpm);

        ProgressUpdateMessage update = new ProgressUpdateMessage(playerId, charIndex, correctChars, wpm);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, update);

        if (charIndex >= room.getTargetText().length()) {
            endGame(room, playerId);
        }
    }

    // ---------- 6. End game ----------

    private void endGame(GameRoom room, String winnerId) {
        room.setState(GameState.FINISHED);

        List<PlayerResult> results = room.getPlayers().stream()
                .map(p -> new PlayerResult(p.getId(), p.getName(), p.getWpm(), p.getCorrectChars()))
                .toList();

        GameOverMessage msg = new GameOverMessage(winnerId, results);
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), msg);

        scheduler.schedule(() -> rooms.remove(room.getId()), 60, TimeUnit.SECONDS);
    }

    // ---------- helpers ----------

    private Player findPlayer(GameRoom room, String playerId) {
        return room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    private void sendToSession(String sessionId, String destination, Object payload) {
        System.out.println("Sending to session: " + sessionId + " -> " + destination);
        messagingTemplate.convertAndSendToUser(sessionId, destination, payload);
    }
}