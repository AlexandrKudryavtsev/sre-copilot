# SRE Copilot — Monitoring Core

Каркас backend для [ТЗ](docs/TASK_WITHOUT_AI.md): Java 21, Spring Boot 4.1.1, Maven,
PostgreSQL 17, Spring Data JPA, Flyway и Swagger/OpenAPI.

## Запуск через Docker Compose

Нужен Docker с Compose. Java и Maven на хосте не требуются.

```sh
cp .env.example .env
docker compose up --build -d --wait
```

- Backend: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Health: <http://localhost:8080/actuator/health>
- Readiness, включая БД: <http://localhost:8080/actuator/health/readiness>
- PostgreSQL: `localhost:5432`, БД и пользователь `sre_copilot`.

Порты и параметры задаются в `.env`; пример содержит настройки для локальной разработки.

```sh
docker compose logs -f backend
docker compose down
```

`down` сохраняет БД в Docker volume. `docker compose down -v` удаляет данные.

## Локальный запуск backend

Нужен JDK 21 или новее; Maven Wrapper включён в проект.

```sh
docker compose up -d --wait postgres
cd backend
./mvnw spring-boot:run
```

Приложение по умолчанию подключается к `jdbc:postgresql://localhost:5432/sre_copilot`
с пользователем `sre_copilot` и паролем `sre_copilot_dev`.
При других настройках Compose передайте соответствующие `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
Spring Boot при локальном запуске не загружает корневой `.env` автоматически.
При запуске одновременно с backend в Compose задайте другой `SERVER_PORT`.

Если системная Java старее 21, сначала задайте `JAVA_HOME` на установленный JDK 21+.

## Сборка и проверки

Из каталога `backend`:

```sh
./mvnw clean verify
```

Интеграционные тесты поднимают отдельный PostgreSQL через Testcontainers;
нужен работающий Docker. Они проверяют запуск приложения, миграции, подключение к БД,
health/readiness, API-заглушки, валидацию, Swagger и CORS.
`./mvnw package` собирает JAR без запуска интеграционных тестов.

## Структура

```text
backend/
  pom.xml
  mvnw, mvnw.cmd, .mvn/      Maven Wrapper
  Dockerfile                сборка и запуск на Java 21
  src/main/java/com/srecopilot/
    SreCopilotApplication
    common/config/          CORS, OpenAPI
    common/exception/       ProblemDetail, обработка ошибок
    user/                   место для пользователей и аутентификации
    project/                controller, service, DTO, enums
    metric/                 controller, service, DTO, enums
    rule/                   controller, service, DTO, enums
    incident/               controller, service, DTO, enums
    monitoring/             настройки; место для scheduler/client/calculator
  src/main/resources/
    application.yaml
    db/migration/           Flyway SQL
  src/test/java/            интеграционные проверки
compose.yaml
.env.example
docs/TASK_WITHOUT_AI.md
```

Схема БД создаётся первой миграцией: `users`, `projects`, `metric_snapshots`,
`monitoring_rules`, `monitoring_rule_states`, `incidents`. Добавлены внешние ключи,
индексы для истории метрик/инцидентов и ограничение одного открытого Incident на Rule.
ID — `BIGINT`, время — `TIMESTAMPTZ`, длительность проверки правила — целые секунды.
Изменения схемы добавляются отдельными миграциями. Hibernate настроен на `validate`,
автоматическое создание таблиц отключено. JPA-сущности и репозитории будут добавляться
при реализации соответствующих модулей.

## API-заглушки

Контроллеры и сервисы имеют типизированные контракты. Корректные запросы к бизнес-API
возвращают `501 Not Implemented` и `application/problem+json`; ошибочные — `400`.
CRUD, сбор метрик, Rule Engine и аутентификация ещё не реализованы.
Проверка подключения к endpoint и переключатели мониторинга также являются заглушками.

| Метод | Путь |
|---|---|
| GET, POST | `/api/projects` |
| GET, PUT, DELETE | `/api/projects/{id}` |
| POST | `/api/projects/{id}/test-connection` |
| POST | `/api/projects/{id}/monitoring/enable` |
| POST | `/api/projects/{id}/monitoring/disable` |
| GET | `/api/projects/{projectId}/metrics/current` |
| GET | `/api/projects/{projectId}/metrics/history?from=...&to=...` |
| GET, POST | `/api/projects/{projectId}/rules` |
| GET, PUT, DELETE | `/api/projects/{projectId}/rules/{ruleId}` |
| POST | `/api/projects/{projectId}/rules/{ruleId}/enable` |
| POST | `/api/projects/{projectId}/rules/{ruleId}/disable` |
| GET | `/api/projects/{projectId}/incidents?status=OPEN&severity=CRITICAL` |
| GET | `/api/incidents/{incidentId}` |

`from` и `to` — обязательные ISO-8601 timestamps, например `2026-10-06T13:00:00Z`.
Параметры `status` и `severity` необязательные. Поле `checkIntervalSeconds`
в запросе правила явно задаёт единицу длительности.

```sh
curl -i http://localhost:8080/api/projects
curl -i -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"Checkout API","endpointUrl":"http://localhost:8081/actuator/prometheus","endpointType":"ACTUATOR"}'
```

## Настройки

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/sre_copilot` | JDBC URL для локального запуска |
| `DB_USERNAME` | `sre_copilot` | Пользователь БД |
| `DB_PASSWORD` | `sre_copilot_dev` | Пароль БД |
| `SERVER_PORT` | `8080` | Порт приложения |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Разрешённые origins frontend |
| `MONITORING_COLLECTION_INTERVAL` | `30s` | Будущий интервал сбора |
| `MONITORING_REQUEST_TIMEOUT` | `5s` | Будущий timeout запросов к endpoint |
| `MONITORING_RECOVERY_CHECKS` | `3` | Будущее число последовательных восстановлений |

Настройки мониторинга типизированы и валидируются при запуске;
фонового scheduler в каркасе пока нет.
