# SRE Copilot — Monitoring Core

Backend для [ТЗ](docs/TASK_WITHOUT_AI.md): Java 21, Spring Boot 4.1.1, Maven,
PostgreSQL 17, Spring Data JPA, Flyway и Swagger/OpenAPI.
Реализованы создание и чтение проектов, проверка подключения, включение мониторинга,
периодический сбор Prometheus/Actuator-метрик и выдача текущих показателей/истории.

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
После включения мониторинга backend автоматически собирает эти метрики и сохраняет Golden Signals.

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
статуса подключения. Тесты мониторинга проверяют interval rates, сбросы счётчиков,
изоляцию сбоев, историю, права доступа и выключение во время HTTP-запроса;
отдельная проверка запускает настоящий scheduler. Unit-тесты проверяют парсинг samples и расчёты.
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
    metric/                 snapshots, repository, controller, service, DTO, enums
    rule/                   controller, service, DTO, enums
    incident/               controller, service, DTO, enums
    monitoring/             scheduler, сборщик, parser, Golden Signals, HTTP client
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
Третья миграция добавляет внутреннюю ревизию мониторинга проекта для проверки результатов
запросов, начатых до выключения/повторного включения мониторинга.

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
OpenMetrics/protobuf пока не поддерживаются. Ручная проверка валидирует формат,
а периодический сбор дополнительно требует исходные метрики адаптера. Redirects не выполняются.
Лимит ответа по умолчанию — 2 MiB;
timeout охватывает весь запрос, включая чтение тела. Сетевой запрос выполняется вне транзакции БД.

Несуществующий проект возвращает `404`; неверный запрос — `400` с `application/problem+json`.
Чтение проектов/метрик, проверка и управление мониторингом ограничены проектами
текущего локального пользователя. Изменение/удаление проекта, правила и инциденты
возвращают `501 Not Implemented`; Rule Engine и аутентификация ещё не реализованы.

| Метод | Путь | Состояние |
|---|---|---|
| GET, POST | `/api/projects` | Реализованы |
| GET, PUT, DELETE | `/api/projects/{id}` | GET реализован; PUT/DELETE — 501 |
| POST | `/api/projects/{id}/test-connection` | Реализован |
| POST | `/api/projects/{id}/monitoring/enable` | Реализован |
| POST | `/api/projects/{id}/monitoring/disable` | Реализован |
| GET | `/api/projects/{projectId}/metrics/current` | 200; 204, если snapshots ещё нет |
| GET | `/api/projects/{projectId}/metrics/history?from=...&to=...` | Реализован |
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

## Периодический сбор метрик

Включите мониторинг созданного проекта. Например, если ответ создания содержит `id = 1`:

```sh
curl -X POST http://localhost:8080/api/projects/1/monitoring/enable
curl -i http://localhost:8080/api/projects/1/metrics/current
curl --get http://localhost:8080/api/projects/1/metrics/history \
  --data-urlencode 'from=2026-10-06T00:00:00Z' \
  --data-urlencode 'to=2026-10-07T00:00:00Z'
curl -X POST http://localhost:8080/api/projects/1/monitoring/disable
```

Scheduler автоматически опрашивает включённые проекты. Между завершением цикла
и началом следующего проходит `MONITORING_COLLECTION_INTERVAL` (по умолчанию 30 с).
Проекты обрабатываются последовательно; циклы в одном процессе не пересекаются.
Первый опрос задаёт начальные счётчики без snapshot, второй позволяет вычислить rates.
Включение не выполняет HTTP-запрос внутри API; сбор начинается в следующем цикле.

Оба типа endpoint используют один адаптер с такими исходными метриками:

| Показатель | Источник и формула |
|---|---|
| Latency, мс | `sum(Δ http_server_requests_seconds_sum) / sum(Δ http_server_requests_seconds_count) × 1000` |
| Traffic, req/s | `sum(Δ http_server_requests_seconds_count) / фактическое время между опросами` |
| Errors, % | Прирост `_count` с `status="5xx"` / прирост всех `_count` × 100; 4xx входят только в общее число |
| Saturation, % | `process_cpu_usage × 100`; максимум при нескольких CPU series |

`status="5xx"` в таблице обозначает любой числовой статус 500–599.
Для каждой серии `_count` и `_sum` должны совпадать все labels; `status` обязателен.
Остальные labels могут различаться между сериями, которые суммируются.
CPU должен быть конечным числом 0–1, счётчики — конечными неотрицательными числами.
Неиспользуемые метрики не участвуют в расчёте. Значения округляются до шести знаков.
Timestamp snapshot — время получения ответа backend, а не необязательный timestamp sample.

Когда новых запросов нет, traffic/latency/errors записываются как 0; latency/errors
в таком snapshot означают отсутствие наблюдений. История возвращается по возрастанию
timestamp, включая обе границы `from`/`to`; обратный интервал даёт `400`.

На ошибке HTTP, timeout, неверном формате или отсутствии необходимых метрик
snapshot не создаётся, проект становится `UNAVAILABLE`, ошибка логируется,
остальные проекты продолжают собираться. Следующий успешный опрос восстанавливает `CONNECTED`
и задаёт новую начальную точку; snapshot появляется после ещё одного успешного опроса.
Сброс любого счётчика, изменение набора series или скачок времени назад также
требуют новой начальной точки без искусственного скачка значений.

Отключение сохраняет историю и отбрасывает результаты незавершённых запросов.
Повторное включение начинает расчёт заново. Начальные счётчики хранятся в памяти
одного backend-процесса: после его перезапуска снова нужны два опроса, история в БД сохраняется.
Запуск нескольких backend-реплик потребует отдельной координации scheduler.
`current` возвращает последний сохранённый snapshot, в том числе после сбоя или отключения;
его актуальность определяется по `timestamp` и состоянию проекта.

Формат samples описан в [Prometheus exposition format](https://prometheus.io/docs/instrumenting/exposition_formats/).
Rule Engine на этом этапе ещё не запускается.

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
| `MONITORING_COLLECTION_INTERVAL` | `30s` | Пауза между циклами сбора, минимум 1 с |
| `MONITORING_SCHEDULER_ENABLED` | `true` | Глобальное включение scheduler, независимо от флагов проектов |
| `MONITORING_REQUEST_TIMEOUT` | `5s` | Timeout проверки endpoint, включая тело ответа |
| `MONITORING_MAX_RESPONSE_BYTES` | `2097152` | Максимальный размер ответа endpoint в байтах |
| `MONITORING_RECOVERY_CHECKS` | `3` | Будущее число последовательных восстановлений |

Настройки мониторинга типизированы и валидируются при запуске.
