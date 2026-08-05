package com.example.typeracer.service;

import com.example.typeracer.entities.Game;
import com.example.typeracer.entities.GameRoom;
import com.example.typeracer.enums.GameState;
import com.example.typeracer.repository.GameRepository;
import com.example.typeracer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {

    private RoomService service;
    private SimpMessagingTemplate messagingTemplate;
    private GameRepository gameRepository;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        gameRepository = mock(GameRepository.class);
        service = new RoomService(messagingTemplate, gameRepository, mock(UserRepository.class));
    }

    @Test
    void duplicateJoinBySameUserIsRejected() {
        service.joinOrCreateRoom("ROOM01", "alice", 1L, "s1");
        assertThrows(IllegalStateException.class,
                () -> service.joinOrCreateRoom("ROOM01", "alice", 1L, "s2"));
    }

    @Test
    void concurrentJoinsNeverExceedCapacity() throws Exception {
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> joinedSizes = new ConcurrentLinkedQueue<>();
        AtomicInteger joined = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    ready.await();
                    GameRoom room = service.joinOrCreateRoom("ROOM02", "user" + n, (long) n, "s" + n);
                    joined.incrementAndGet();
                    joinedSizes.add(room.getPlayers().size());
                } catch (Exception e) {
                    errors.add(e.getMessage());
                }
            });
        }

        ready.countDown();
        pool.shutdown();
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(threads, joined.get() + errors.size(), "every thread must either join or be rejected");
        assertTrue(joined.get() <= 4, "no more than 4 players may join");
        assertEquals(joined.get(), joinedSizes.size(), "each successful join saw a consistent room");
        assertTrue(joinedSizes.stream().allMatch(size -> size <= 4));
    }

    @Test
    void fullGameFlowPersistsResult() throws Exception {
        GameRoom room = service.joinOrCreateRoom("ROOM03", "alice", 1L, "s1");
        service.joinOrCreateRoom("ROOM03", "bob", 2L, "s2");

        service.setReady("ROOM03", "alice", true);
        service.setReady("ROOM03", "bob", true);
        service.startGame("ROOM03", "alice");

        Thread.sleep(4200); // wait out the 3s countdown

        String target = room.getTargetText();
        service.updateProgress("ROOM03", "bob", target.length(), target.length(), 0);
        service.updateProgress("ROOM03", "alice", target.length() - 1, target.length() - 1, 1);

        assertEquals(GameState.FINISHED, room.getState());

        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository, timeout(5000)).save(captor.capture());

        Game saved = captor.getValue();
        assertEquals("ROOM03", saved.getRoomCode());
        assertEquals(2, saved.getResults().size());
        long winners = saved.getResults().stream().filter(r -> r.isWon()).count();
        assertEquals(1, winners);
        assertTrue(saved.getResults().stream().anyMatch(r -> r.getUsername().equals("bob") && r.getRankNo() == 1));
    }

    @Test
    void disconnectRemovesPlayerAndRoomWhenEmpty() {
        service.joinOrCreateRoom("ROOM04", "alice", 1L, "s1");
        GameRoom room = service.joinOrCreateRoom("ROOM04", "bob", 2L, "s2");

        service.handleDisconnect("s2");
        assertEquals(1, room.getPlayers().size());
        assertEquals("alice", room.getPlayers().get(0).getName());

        service.handleDisconnect("s1");
        assertEquals(0, room.getPlayers().size());
    }
}
