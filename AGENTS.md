# TypeRacer — Multiplayer Typing Race Game

## Repo layout

```
TypeRacer/           ← Spring Boot backend (this directory)
frontend/            ← React + Vite frontend (sibling, see frontend/AGENTS.md)
```

## Quick start

**Prerequisites:** MySQL running on `localhost:3306`. The database `typeracer` is auto-created (`createDatabaseIfNotExist=true`). Connection values default to `root` / `Darshan@123` and can be overridden with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars.

```bash
./mvnw spring-boot:run          # backend on :8080
# In another terminal:
cd ../frontend; npm install; npm run dev  # frontend on :3000
```

Open `http://localhost:3000`. Two browser tabs = two players.

Other commands:
```bash
./mvnw test               # 4 RoomService unit tests + context-loads test
npm run build              # frontend prod build
npm run lint               # frontend oxlint
```

## Architecture

### Backend packages (`com.example.typeracer.*`)

| Layer | Files | What |
|---|---|---|
| `controller/` | `AuthController` | REST `/auth/register`, `/auth/login` |
| `controller/` | `ApiController` | REST `/api/me`, `/api/me/games` (auth required) |
| `controller/` | `GameWebSocketController` | STOMP `/app/{join,ready,start,progress}`, disconnect listener, error queue |
| `controller/` | `GlobalExceptionHandler` | Maps `IllegalArgument/IllegalState` → 400/409 JSON, validation → 400 |
| `service/` | `RoomService` | Game room lifecycle: join/create, countdown, progress, WPM/accuracy calc, end-game, persistence, disconnect cleanup |
| `service/` | `AuthService`, `JwtService` | User registration/login, JWT token ops |
| `service/` | `StatsService` | Aggregate profile stats + recent game history from DB |
| `repository/` | `UserRepository`, `GameRepository`, `GameResultRepository` | JPA repos; `GameResultRepository` has the aggregate queries (games, wins, avg/best WPM, avg accuracy) |
| `entities/` | `User`, `Game`, `GameResult` (JPA), `GameRoom`, `Player` (in-memory POJOs) | `Game` = persisted game record, `GameResult` = per-player persisted result |
| `enums/` | `GameState` | `WAITING → COUNTDOWN → IN_PROGRESS → FINISHED` |
| `dtos/` | 15+ message types | Inbound/outbound STOMP + REST DTOs |
| `config/` | `WebSocketConfig`, `SecurityConfig`, `JwtAuthInterceptor`, `JwtAuthFilter`, `UserPrincipal` | Broker config, REST JWT filter, WS auth interceptor |

Key facts:
- **Hybrid persistence:** User auth and game results are JPA/MySQL-backed. Live rooms are in-memory (`ConcurrentHashMap` in `RoomService`), ephemeral — removed 60s after a game ends or when empty.
- **Game results persist on finish:** when a race ends (first finisher or 5-min timeout), `RoomService` writes a `games` row + one `game_results` row per player (wpm, accuracy, chars, errors, rank, won). `users` never holds derived stats — they're computed on read via `GameResultRepository` aggregates.
- **REST endpoints:** `POST /auth/register`, `POST /auth/login` (both permit JWTs); `GET /api/me` (profile + aggregate stats), `GET /api/me/games` (recent 10 races) — both require `Authorization: Bearer <jwt>`.
- **WebSocket auth required:** Every STOMP `CONNECT` frame must carry `Authorization: Bearer <jwt>`. Rejected with 403 if missing/expired. Errors raised inside `/app/**` handlers are sent to the sender's `/user/queue/errors`.
- **SockJS enabled** on the server endpoint (`/ws` with `.withSockJS()`); the native raw WebSocket endpoint is `/ws/websocket` (what the frontend uses).
- **STOMP:** Client sends to `/app/{join,ready,start,progress}`, server broadcasts to `/topic/room/{roomId}`, user-queued to `/queue/room-joined`.
- **Concurrency model:** per-room `synchronized(room)` guards all state mutations (join, ready, start, progress, disconnect). Room creation is atomic via `computeIfAbsent` + collision-checked room codes. `Player` list is `CopyOnWriteArrayList`. Capacity is `MAX_PLAYERS_PER_ROOM = 4`.
- **Disconnects:** a `SessionDisconnectEvent` listener removes the player from the room; empty rooms are dropped, the host is re-elected if they left while waiting, and a disconnected mid-race player simply finishes last in the persisted results.

## Gotchas

**MySQL required for startup:** `application.properties` points at `jdbc:mysql://localhost:3306/typeracer`. The app won't start without a running MySQL. Defaults are `root` / `Darshan@123` — replace via env vars (`DB_USERNAME` / `DB_PASSWORD`) for production.

**JWT secret:** `jwt.secret` defaults to a dev-only string — override with `JWT_SECRET` in production.

**`rank` is reserved in MySQL:** the persisted column is `rank_no` (entity field `rankNo`); the REST DTO field is still `rank`.

**Rooms are not persisted while live:** a server restart mid-game drops all rooms. Only finished games are written to the DB.

**Countdown:** implemented as a chained `scheduler.schedule` recursion (3 → 0), clean no-exception cancellation. A 5-minute forced-end fallback guarantees no room gets stuck in `IN_PROGRESS`.

## Testing

- `RoomServiceTest`: unit tests (Mockito, no DB) covering duplicate joins, concurrent joins never exceeding capacity, full game flow → persistence, and disconnect cleanup.
- `TypeRacerApplicationTests`: context loads (needs MySQL up).
- No WebSocket integration tests yet. `spring-boot-starter-websocket-test` is on the classpath.

## Frontend

See `../frontend/AGENTS.md`.
