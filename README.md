# SRE Copilot — Monitoring Core

Backend для [ТЗ](docs/TASK_WITHOUT_AI.md): Java 21, Spring Boot 4.1.1, Maven,
PostgreSQL 17, Spring Data JPA, Flyway и Swagger/OpenAPI.
Реализованы создание проектов, чтение списка/одного проекта и проверка подключения
к Prometheus/Actuator endpoint.

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
- Генератор тестовых метрик: <http://localhost:8081/metrics>.

Порты и параметры задаются в `.env`; пример содержит настройки для локальной разработки.

```sh
docker compose logs -f backend
docker compose down
```

`down` сохраняет БД в Docker volume. `docker compose down -v` удаляет данные.

## Тестовый сервис для мониторинга

Рядом с backend находится [demo-service](demo-service/README.md): приложение на Python
без внешних зависимостей, генерирующее Prometheus-метрики. Оно входит в Compose;
отдельный запуск: `docker compose up --build -d --wait demo-service`.

Для проекта укажите `endpointType = PROMETHEUS` и `endpointUrl = http://demo-service:8081/metrics`
при запуске backend в Compose, либо `http://localhost:8081/metrics` при локальном запуске backend.
Также доступен `/actuator/prometheus` для типа `ACTUATOR`.
Сценарии `normal`, `slow`, `errors`, `overloaded`, `unavailable` переключаются без перезапуска:

```sh
curl -X POST http://localhost:8081/scenario \
  -H 'Content-Type: application/json' -d '{"name":"slow"}'
```

Счётчики запросов и длительностей накапливаются со временем; CPU выдаётся как gauge.
`unavailable` возвращает HTTP 503 на endpoint метрик, а `normal` восстанавливает его.
Backend сейчас поддерживает ручную проверку подключения; периодический сбор ещё предстоит реализовать.

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
health/readiness, API-заглушки, валидацию, Swagger и CORS. Сценарии проекта проверяют
сохранение в PostgreSQL, HTTP-запросы к локальному серверу, ошибки, таймауты и восстановление
статуса подключения. Отдельные unit-тесты проверяют синтаксис Prometheus samples.
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
    user/                   текущий локальный пользователь
    project/                entity, repository, controller, service, DTO, validation
    metric/                 controller, service, DTO, enums
    rule/                   controller, service, DTO, enums
    incident/               controller, service, DTO, enums
    monitoring/             HTTP client, проверка формата метрик, настройки
  src/main/resources/
    application.yaml
    db/migration/           Flyway SQL
  src/test/java/            интеграционные проверки
compose.yaml
demo-service/               генератор метрик, сценарии нагрузки/ошибок, Dockerfile
.env.example
docs/TASK_WITHOUT_AI.md
```

Схема БД создаётся первой миграцией: `users`, `projects`, `metric_snapshots`,
`monitoring_rules`, `monitoring_rule_states`, `incidents`. Добавлены внешние ключи,
индексы для истории метрик/инцидентов и ограничение одного открытого Incident на Rule.
ID — `BIGINT`, время — `TIMESTAMPTZ`, длительность проверки правила — целые секунды.
Изменения схемы добавляются отдельными миграциями. Hibernate настроен на `validate`,
автоматическое создание таблиц отключено. Модуль проектов использует JPA-сущность и repository.
Вторая миграция добавляет пользователя `local@srecopilot.test`: он является владельцем
всех проектов, создаваемых через API на этом этапе. Аутентификация будет добавлена позже;
вместо пароля у локального пользователя записан маркер отключённого входа.

## API

Создание проекта возвращает `201 Created`, тело проекта и заголовок `Location`.
При создании `connectionStatus = UNKNOWN`, `monitoringEnabled = false`; HTTP-запрос
к endpoint выполняется отдельной операцией `test-connection`.

Проверка соединения возвращает `200 OK` с `connectionStatus` и диагностическим `message`.
Статус `CONNECTED` означает HTTP 200 с валидными текстовыми Prometheus samples.
При HTTP-ошибке, недоступности, таймауте или неподходящем ответе статус — `UNAVAILABLE`.
Результат сохраняется в проекте, обновляя `updatedAt`. Повторная успешная проверка
восстанавливает `CONNECTED`. Проверка не создаёт `MetricSnapshot` и не включает мониторинг.

Проверяются HTTP(S) URL, заголовок `Content-Type: text/plain`, UTF-8 и синтаксис samples
в [Prometheus text format](https://prometheus.io/docs/instrumenting/exposition_formats/#prometheus-text-format).
Как минимум один sample должен присутствовать. Клиент запрашивает формат 0.0.4;
OpenMetrics/protobuf и проверка наличия исходных метрик для четырёх Golden Signals
относятся к последующим этапам. Redirects не выполняются. Лимит ответа по умолчанию — 2 MiB;
timeout охватывает весь запрос, включая чтение тела. Сетевой запрос выполняется вне транзакции БД.

Несуществующий проект возвращает `404`; неверный запрос — `400` с `application/problem+json`.
Чтение и проверка ограничены проектами текущего локального пользователя.
Остальные бизнес-операции возвращают `501 Not Implemented`. Изменение/удаление проекта,
сбор метрик, переключатели мониторинга, Rule Engine и аутентификация ещё не реализованы.

| Метод | Путь | Состояние |
|---|---|---|
| GET, POST | `/api/projects` | Реализованы |
| GET, PUT, DELETE | `/api/projects/{id}` | GET реализован; PUT/DELETE — 501 |
| POST | `/api/projects/{id}/test-connection` | Реализован |
| POST | `/api/projects/{id}/monitoring/enable` | 501 |
| POST | `/api/projects/{id}/monitoring/disable` | 501 |
| GET | `/api/projects/{projectId}/metrics/current` | 501 |
| GET | `/api/projects/{projectId}/metrics/history?from=...&to=...` | 501 |
| GET, POST | `/api/projects/{projectId}/rules` | 501 |
| GET, PUT, DELETE | `/api/projects/{projectId}/rules/{ruleId}` | 501 |
| POST | `/api/projects/{projectId}/rules/{ruleId}/enable` | 501 |
| POST | `/api/projects/{projectId}/rules/{ruleId}/disable` | 501 |
| GET | `/api/projects/{projectId}/incidents?status=OPEN&severity=CRITICAL` | 501 |
| GET | `/api/incidents/{incidentId}` | 501 |

`from` и `to` — обязательные ISO-8601 timestamps, например `2026-10-06T13:00:00Z`.
Параметры `status` и `severity` необязательные. Поле `checkIntervalSeconds`
в запросе правила явно задаёт единицу длительности.

```sh
curl -i http://localhost:8080/api/projects
curl -i -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"Checkout API","endpointUrl":"http://host.docker.internal:8081/actuator/prometheus","endpointType":"ACTUATOR"}'
```

Используйте `id` из ответа при проверке и чтении, например для `id = 1`:

```sh
curl -i -X POST http://localhost:8080/api/projects/1/test-connection
curl -i http://localhost:8080/api/projects/1
```

В Docker Desktop `host.docker.internal` адресует сервис на хосте. При локальном
запуске backend используйте `localhost:8081`; для сервиса в той же Docker-сети — его DNS-имя.
Endpoint должен отдавать метрики, например `/actuator/prometheus`, а не JSON `/actuator/health`.

## Настройки

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/sre_copilot` | JDBC URL для локального запуска |
| `DB_USERNAME` | `sre_copilot` | Пользователь БД |
| `DB_PASSWORD` | `sre_copilot_dev` | Пароль БД |
| `SERVER_PORT` | `8080` | Порт приложения |
| `DEMO_SERVICE_PORT` | `8081` | Внешний порт тестового сервиса в Compose |
| `DEMO_SCENARIO` | `normal` | Начальный сценарий тестового сервиса |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Разрешённые origins frontend |
| `MONITORING_COLLECTION_INTERVAL` | `30s` | Будущий интервал сбора |
| `MONITORING_REQUEST_TIMEOUT` | `5s` | Timeout проверки endpoint, включая тело ответа |
| `MONITORING_MAX_RESPONSE_BYTES` | `2097152` | Максимальный размер ответа endpoint в байтах |
| `MONITORING_RECOVERY_CHECKS` | `3` | Будущее число последовательных восстановлений |

Настройки мониторинга типизированы и валидируются при запуске;
фонового scheduler в каркасе пока нет.
