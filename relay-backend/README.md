# Relay backend

Spring Boot API for the Relay chatbot: JWT login (email or username), chat sessions
with AI-generated titles, pin/rename/delete, and streamed Gemini replies, all stored
in PostgreSQL.

## Prerequisites

- JDK 17+, Maven (or an IDE with Maven support)
- PostgreSQL running locally, with an empty database: `CREATE DATABASE relay_chat;`

## Configuration and secrets

`application.yml` holds no secrets. Real values go in one of two places:

**Local development:** copy `src/main/resources/secrets.example.yml` to
`src/main/resources/secrets.yml` and fill it in. That file is git-ignored.

**Production:** set environment variables instead:

| Variable         | Purpose                                              | Default                                      |
|------------------|------------------------------------------------------|----------------------------------------------|
| `JWT_SECRET`     | Signs login tokens (32+ chars, required)             | none; the app won't start without it         |
| `GEMINI_API_KEY` | Gemini API key                                       | none; AI replies fail until set              |
| `DB_PASSWORD`    | Postgres password                                    | empty                                        |
| `DB_URL`         | JDBC URL                                             | `jdbc:postgresql://localhost:5432/relay_chat`|
| `DB_USERNAME`    | Postgres user                                        | `postgres`                                   |
| `GEMINI_MODEL`   | Model name                                           | `gemini-3.1-flash-lite`                      |

Changing `JWT_SECRET` signs everyone out (old tokens stop validating).

## Run

```
mvn spring-boot:run
```

Tables and new columns are created automatically (`ddl-auto: update`).

## Assistant persona

The system prompt is `app.assistant.system-prompt` in `application.yml`. Edit it and
restart. Today's date is appended automatically.

## Gemini errors

Temporary failures (429, 500, 502, 503, 504) are retried up to 3 times with a short
pause before the user sees an error. Other failures (bad key, unknown model) are not
retried. Model names change over time; if you get a 404, check
https://ai.google.dev/gemini-api/docs/changelog for the current name.

## Endpoints

| Method | Path                                  | Auth | Purpose                                  |
|--------|---------------------------------------|------|------------------------------------------|
| POST   | `/api/auth/register`                  | no   | Create account (email, username, password)|
| POST   | `/api/auth/login`                     | no   | Log in with `identifier` (email or username) |
| GET    | `/api/sessions`                       | yes  | List chats (pinned first)                |
| POST   | `/api/sessions`                       | yes  | Create a chat                            |
| PUT    | `/api/sessions/{id}`                  | yes  | Rename and/or pin: `{"title":..,"pinned":..}` |
| DELETE | `/api/sessions/{id}`                  | yes  | Delete a chat and its messages           |
| GET    | `/api/sessions/{id}/messages`         | yes  | Message history                          |
| POST   | `/api/sessions/{id}/messages`         | yes  | Send a message (one-shot reply)          |
| POST   | `/api/sessions/{id}/messages/stream`  | yes  | Send a message (streamed reply)          |

Protected endpoints need `Authorization: Bearer <token>`.
