# TypeRacer — Spring Boot WebSocket Multiplayer Typing Game

## Quick start

```bash
cd TypeRacer
./mvnw spring-boot:run        # run the app
./mvnw test                   # run tests
./mvnw package                # build jar
```

## Tech stack

- Java 17, Spring Boot 4.1.0, Maven
- STOMP over WebSocket (SockJS fallback enabled)
- Lombok (annotation processing configured in pom.xml)
- No database — all state lives in-memory (`ConcurrentHashMap` in `RoomService`)

## Architecture

Single-module app. The game is purely WebSocket-driven; there is no REST API beyond a health-check ping.

- `controller/GameWebSocketController` — STOMP message handler (`/join`, `/ready`, `/start`, `/progress`)
- `service/RoomService` — all game logic: room lifecycle, countdown, progress tracking, WPM calc, end-game
- `entities/` — `GameRoom`, `Player` (Lombok POJOs, no JPA)
- `enums/GameState` — `WAITING → COUNTDOWN → IN_PROGRESS → FINISHED`
- `dtos/` — inbound/outbound STOMP message types
- `config/WebSocketConfig` — broker config (`/topic`, `/queue` prefixes; `/app` destination prefix)

## Gotchas

**Package directory mismatch:** The filesystem directories are `controller/` and `service/`, but the Java package declarations say `controllers` and `services`. Both compile (the `package` declaration is what matters), but don't rename one without the other or you'll break it.

**Rooms are ephemeral:** `RoomService.rooms` is an in-memory map. A room is removed 60 seconds after the game ends. Restarting the server wipes all rooms.

**Hardcoded 2-player cap:** `MAX_PLAYERS_PER_ROOM = 2` in `RoomService`.

**Text bank is small:** Only 3 sentences in `TEXT_BANK`. Add more or replace with an API when needed.

**STOMP destinations:** Client sends to `/app/{join,ready,start,progress}`. Server broadcasts to `/topic/room/{roomId}`. User-specific messages go to `/queue/room-joined`.

**Countdown uses a scheduled executor:** `startCountdown` stops itself by throwing a `RuntimeException("stop")` — ugly but functional.

## Testing

Only one test class exists (`TypeRacerApplicationTests`). There is no WebSocket integration test coverage. When adding tests, use `spring-boot-starter-websocket-test` which is already a dependency.
