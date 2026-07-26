# TypeRacer — Multiplayer Typing Race Game

## Repo layout

```
TypeRacer/          ← Spring Boot backend (this directory)
frontend/           ← React + Vite frontend (sibling directory)
```

## Quick start

**Backend** (port 8080):
```bash
cd TypeRacer
./mvnw spring-boot:run
```

**Frontend** (port 3000, proxies /ws to backend):
```bash
cd ../frontend
npm install
npm run dev
```

Open `http://localhost:3000`. Two browser tabs = two players.

Other commands:
```bash
./mvnw test               # backend tests
npm run build              # frontend production build
npm run lint               # frontend lint
```

## Tech stack

**Backend:** Java 17, Spring Boot 4.1.0, Maven, STOMP over WebSocket (SockJS fallback), Lombok, in-memory state

**Frontend:** React 19, Vite 8, @stomp/stompjs (native WebSocket, no SockJS client needed)

## Architecture

The game is purely WebSocket-driven; no REST API.

### Backend

- `controller/GameWebSocketController` — STOMP message handler (`/join`, `/ready`, `/start`, `/progress`)
- `service/RoomService` — all game logic: room lifecycle, countdown, progress tracking, WPM calc, end-game
- `entities/` — `GameRoom`, `Player` (Lombok POJOs, no JPA)
- `enums/GameState` — `WAITING → COUNTDOWN → IN_PROGRESS → FINISHED`
- `dtos/` — inbound/outbound STOMP message types
- `config/WebSocketConfig` — broker config, user principal interceptor

### Frontend

- `src/hooks/useGame.js` — all game state + STOMP connection in one hook
- `src/components/` — JoinScreen, Lobby, Race, Results, Countdown
- `src/lib/stomp.js` — STOMP client factory
- Vite proxy: `/ws` → `localhost:8080` in dev mode

## Gotchas

**Package directory mismatch:** The filesystem directories are `controller/` and `service/`, but the Java package declarations say `controllers` and `services`. Both compile (the `package` declaration is what matters), but don't rename one without the other or you'll break it.

**Rooms are ephemeral:** `RoomService.rooms` is an in-memory map. A room is removed 60 seconds after the game ends. Restarting the server wipes all rooms.

**Hardcoded 2-player cap:** `MAX_PLAYERS_PER_ROOM = 2` in `RoomService`.

**Text bank is small:** Only 3 sentences in `TEXT_BANK`. Add more or replace with an API when needed.

**STOMP destinations:** Client sends to `/app/{join,ready,start,progress}`. Server broadcasts to `/topic/room/{roomId}`. User-specific messages go to `/queue/room-joined`.

**Countdown uses a scheduled executor:** `startCountdown` stops itself by throwing a `RuntimeException("stop")` — ugly but functional.

## Testing

Only one test class exists (`TypeRacerApplicationTests`). There is no WebSocket integration test coverage. When adding tests, use `spring-boot-starter-websocket-test` which is already a dependency.
