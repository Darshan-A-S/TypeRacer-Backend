# TypeRacer — Multiplayer Typing Race Game

## Repo layout

```
TypeRacer/           ← Spring Boot backend (this directory)
frontend/            ← React + Vite frontend (sibling, see frontend/AGENTS.md)
```

## Quick start

**Prerequisites:** MySQL running on `localhost:3306`, database `typeracer` (auto-created with `createDatabaseIfNotExist=true`).

```bash
./mvnw spring-boot:run          # backend on :8080
# In another terminal:
cd ../frontend; npm install; npm run dev  # frontend on :3000
```

Open `http://localhost:3000`. Two browser tabs = two players.

Other commands:
```bash
./mvnw test               # single context-loads test
npm run build              # frontend prod build
npm run lint               # frontend oxlint
```

## Architecture

### Backend packages (`com.example.typeracer.*`)

| Layer | Files | What |
|---|---|---|
| `controller/` | `AuthController` | REST `/auth/register`, `/auth/login` |
| `controllers/` | `GameWebSocketController` | STOMP `/app/{join,ready,start,progress}` |
| `service/` | `AuthService`, `JwtService` | User registration/login, JWT token ops |
| `services/` | `RoomService` | Game room lifecycle, countdown, WPM calc, end-game |
| `repository/` | `UserRepository` | JPA repo for `User` entity |
| `entities/` | `User` (JPA, table `users`), `GameRoom`, `Player` (POJOs) | |
| `enums/` | `GameState` | `WAITING → COUNTDOWN → IN_PROGRESS → FINISHED` |
| `dtos/` | 13 message types | Inbound/outbound STOMP + REST DTOs |
| `config/` | `WebSocketConfig`, `SecurityConfig`, `JwtAuthInterceptor`, `UserPrincipal` | Broker config, CSRF disabled, WS auth interceptor |

Key facts:
- **Hybrid:** User auth is JPA/MySQL-backed (REST). Game rooms are in-memory (`ConcurrentHashMap` in `RoomService`), ephemeral 60s after game ends.
- **REST endpoints:** `POST /auth/register`, `POST /auth/login` — both return/permit JWTs.
- **WebSocket auth required:** Every STOMP `CONNECT` frame must carry `Authorization: Bearer <jwt>`. Rejected with 403 if missing/expired. Access token from `POST /auth/login` response.
- **SockJS enabled** on the server endpoint (`/ws` with `.withSockJS()`). Frontend uses native WebSocket via `@stomp/stompjs` but SockJS is available.
- **STOMP:** Client sends to `/app/{join,ready,start,progress}`, server broadcasts to `/topic/room/{roomId}`, user-queued to `/queue/room-joined`.

## Gotchas

**Package name inconsistency:** Filesystem dirs are `controller/` and `service/`, but `GameWebSocketController` declares `package controllers` (plural) and `RoomService` declares `package services` (plural). The other files use singular `controller`/`service`. Both compile fine — never rename a dir without fixing the matching package declaration.

**MySQL required for startup:** `application.properties` points at `jdbc:mysql://localhost:3306/typeracer`. The app won't start without a running MySQL. Credentials (`root` / `Darshan@123`) and JWT secret (`jwt.secret`) are hardcoded — replace for production.

**Hardcoded 2-player cap:** `MAX_PLAYERS_PER_ROOM = 2` in `RoomService`.

**Text bank is small:** 3 sentences in `TEXT_BANK`.

**Countdown:** `startCountdown` stops by throwing `RuntimeException("stop")` — functional but ugly.

## Testing

Only `TypeRacerApplicationTests` (context loads). No WebSocket integration tests. `spring-boot-starter-websocket-test` is on the classpath when you add them.

## Frontend

See `../frontend/AGENTS.md`.
