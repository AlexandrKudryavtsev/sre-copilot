> Архив README до переработки 9 октября 2026 года. Содержит подробности реализации и локальной разработки; актуальная инструкция — в [README](../README.md).

# SRE Copilot — Monitoring Core

Backend для [ТЗ](TASK_WITHOUT_AI.md): Java 21, Spring Boot 4.1.1, Maven,
PostgreSQL 17, Spring Data JPA, Flyway и Swagger/OpenAPI.
Реализованы CRUD проектов, проверка подключения, включение мониторинга,
периодический сбор Prometheus/Actuator-метрик, правила и автоматическое открытие/закрытие
инцидентов. API возвращает текущие метрики, историю, правила и контекст инцидентов.

В [front](../front/README.md) реализован frontend на React/TypeScript: управление проектами,
Overview с реальными Golden Signals и историей, просмотр списка и деталей инцидентов,
создание/редактирование правил, включение/отключение и удаление с сохранением истории.
Rules показывает прогресс проверок и открытый инцидент; правила доступны из sidebar проекта.
Полный сценарий открытия и восстановления на настоящем Actuator-сервисе проверен;
результаты — в [отчёте приёмки](ACCEPTANCE_REPORT.md).

Пошаговая инструкция для ручного запуска и создания тестовых инцидентов —
[MANUAL_TESTING.md](MANUAL_TESTING.md).

## Запуск через Docker Compose

Нужен Docker с Compose. Java и Maven на хосте не требуются.

```sh
test -f .env || cp .env.example .env
docker compose up --build -d --wait
```

- Backend: <http://localhost:8080>
- Frontend: <http://localhost:5173>
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

Рядом с backend находится [demo-service](../demo-service/README.md): приложение на Python
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
health/readiness, обработку ошибок API, валидацию, Swagger и CORS. Сценарии проекта проверяют
CRUD в PostgreSQL, HTTP-запросы к локальному серверу, пригодность метрик для Golden Signals,
ошибки, таймауты и восстановление статуса подключения. Проверяются права владельца,
каскадное удаление данных и отбрасывание HTTP-ответов после изменения/удаления проекта.
Тесты мониторинга проверяют interval rates, сбросы счётчиков,
изоляцию сбоев, историю, права доступа и выключение во время HTTP-запроса;
отдельная проверка запускает настоящий scheduler. Unit-тесты проверяют парсинг samples и расчёты.
Тесты Rule Engine проверяют интервалы, все показатели/операторы, последовательности
нарушений/восстановлений, конкурентную обработку, сохранение контекста и транзакционный
откат snapshot, состояния правила и инцидента при ошибке БД. Проверяется полный цикл
HTTP endpoint → сборщик → Rule Engine → Incidents API.
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
    rule/                   правила, runtime state, Rule Engine, API
    incident/               incidents, repository, controller, service, DTO, enums
    monitoring/             scheduler, сборщик, parser, Golden Signals, HTTP client
  src/main/resources/
    application.yaml
    db/migration/           Flyway SQL
  src/test/java/            интеграционные проверки
compose.yaml
front/                      React + Vite, frontend, Nginx, Dockerfile
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
Четвёртая миграция создаёт runtime state для существующих правил и связывает его
с уже открытыми инцидентами. API новых правил создаёт правило и state в одной транзакции.

## API

Создание проекта возвращает `201 Created`, тело проекта и заголовок `Location`.
При создании `connectionStatus = UNKNOWN`, `monitoringEnabled = false`; HTTP-запрос
к endpoint выполняется отдельной операцией `test-connection`.

Проверка соединения возвращает `200 OK` с `connectionStatus` и диагностическим `message`.
Статус `CONNECTED` означает HTTP 200 с валидными текстовыми Prometheus samples,
пригодными для адаптера Golden Signals: нужны совпадающие серии
`http_server_requests_seconds_count` и `_sum` с числовым label `status`, а также
`process_cpu_usage` в диапазоне 0–1. Значения счётчиков должны быть конечными и неотрицательными.
При HTTP-ошибке, недоступности, таймауте, отсутствии необходимых метрик или неподходящем
ответе статус — `UNAVAILABLE`, а `message` объясняет причину.
Результат сохраняется в проекте, обновляя `updatedAt`. Повторная успешная проверка
восстанавливает `CONNECTED`. Проверка не создаёт `MetricSnapshot` и не включает мониторинг.

Проверяются HTTP(S) URL, заголовок `Content-Type: text/plain`, UTF-8 и синтаксис samples
в [Prometheus text format](https://prometheus.io/docs/instrumenting/exposition_formats/#prometheus-text-format).
Как минимум один sample должен присутствовать. Клиент запрашивает формат 0.0.4;
OpenMetrics/protobuf пока не поддерживаются. Ручная проверка и периодический сбор
используют одинаковую проверку исходных метрик адаптера. Redirects не выполняются.
Лимит ответа по умолчанию — 2 MiB;
timeout охватывает весь запрос, включая чтение тела. Сетевой запрос выполняется вне транзакции БД.

Несуществующий проект возвращает `404`; неверный запрос — `400` с `application/problem+json`.
Чтение проектов/метрик, проверка и управление мониторингом ограничены проектами
текущего локального пользователя. API правил и инцидентов также проверяет владельца
проекта. Аутентификация ещё не реализована.

`PUT /api/projects/{id}` принимает те же поля, что и создание, и возвращает `200` с проектом.
При смене `endpointUrl` или `endpointType` статус сбрасывается в `UNKNOWN`, последовательности
нарушений/восстановлений сбрасываются, а сборщик начинает новую базовую точку счётчиков.
Прежние snapshots, правила и инциденты сохраняются; флаг мониторинга не меняется.
Редактирование только имени/описания сохраняет статус подключения и состояние мониторинга.
`DELETE /api/projects/{id}` возвращает `204` и удаляет проект вместе с его snapshots,
правилами, runtime state и инцидентами. Чужой или отсутствующий проект возвращает `404`.
Результаты сбора, начатого до смены источника или удаления проекта, отбрасываются.
Если конфигурация мониторинга изменилась во время `test-connection`, API возвращает `409`:
проверку нужно повторить. Удаление проекта во время проверки приводит к `404`.

| Метод | Путь | Состояние |
|---|---|---|
| GET, POST | `/api/projects` | Реализованы |
| GET, PUT, DELETE | `/api/projects/{id}` | GET/PUT 200; DELETE 204 |
| POST | `/api/projects/{id}/test-connection` | Реализован |
| POST | `/api/projects/{id}/monitoring/enable` | Реализован |
| POST | `/api/projects/{id}/monitoring/disable` | Реализован |
| GET | `/api/projects/{projectId}/metrics/current` | 200; 204, если snapshots ещё нет |
| GET | `/api/projects/{projectId}/metrics/history?from=...&to=...` | Реализован |
| GET, POST | `/api/projects/{projectId}/rules` | GET 200; POST 201 + Location |
| GET, PUT, DELETE | `/api/projects/{projectId}/rules/{ruleId}` | GET/PUT 200; DELETE 204 или 409 при открытом инциденте |
| POST | `/api/projects/{projectId}/rules/{ruleId}/enable` | Реализован |
| POST | `/api/projects/{projectId}/rules/{ruleId}/disable` | Реализован |
| GET | `/api/projects/{projectId}/incidents?status=OPEN&severity=CRITICAL` | Реализован |
| GET | `/api/incidents/{incidentId}` | Реализован |

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
и началом следующего проходит `MONITORING_COLLECTION_INTERVAL` (по умолчанию 5 с).
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

## Правила и инциденты

Новое правило содержит `name`, `metric`, `operator`, `threshold`, `severity`,
`checkIntervalSeconds`, `consecutiveViolations`, `enabled`. Обязательны все поля.
Показатели: `LATENCY` (мс), `TRAFFIC` (req/s), `ERRORS`/`SATURATION` (%).
Операторы: `GREATER_THAN`, `GREATER_OR_EQUAL`, `LESS_THAN`, `LESS_OR_EQUAL`.
Severity: `WARNING`, `CRITICAL`; порог неотрицательный, интервал и число нарушений — от 1.

Пример для проекта с `id = 1`, подключённого к demo-service:

```sh
curl -i -X POST http://localhost:8080/api/projects/1/rules \
  -H 'Content-Type: application/json' \
  -d '{"name":"High CPU","metric":"SATURATION","operator":"GREATER_OR_EQUAL","threshold":80,"severity":"CRITICAL","checkIntervalSeconds":30,"consecutiveViolations":3,"enabled":true}'
curl -X POST http://localhost:8080/api/projects/1/monitoring/enable
curl -X POST http://localhost:8081/scenario \
  -H 'Content-Type: application/json' -d '{"name":"overloaded"}'
# После трёх нарушений правила:
curl 'http://localhost:8080/api/projects/1/incidents?status=OPEN&severity=CRITICAL'
curl http://localhost:8080/api/projects/1/rules
# Восстановление: после трёх здоровых проверок инцидент станет RESOLVED.
curl -X POST http://localhost:8081/scenario \
  -H 'Content-Type: application/json' -d '{"name":"normal"}'
curl 'http://localhost:8080/api/projects/1/incidents?status=RESOLVED'
```

Правила проверяются на новых snapshots. Первая проверка выполняется на следующем
snapshot после создания/включения правила, последующие — когда прошло не менее
`checkIntervalSeconds` с последней проверки. Если сбор идёт раз в 30 с, правило
с интервалом 5 с всё равно проверяется только при поступлении нового snapshot.
Повторные и более старые snapshots не увеличивают счётчики. История не переигрывается.

`RuleResponse.runtimeState` показывает `state`, `consecutiveViolations`,
`consecutiveRecoveries`, `activeIncidentId`, `lastCheckedAt`, `lastViolationAt`.
Переходы: `HEALTHY → VIOLATING → INCIDENT_OPEN → HEALTHY`.
Здоровая проверка до открытия сбрасывает нарушения. После N нарушений создаётся
один `OPEN` incident; повторные нарушения сохраняют его. Для закрытия нужны
`MONITORING_RECOVERY_CHECKS` последовательных здоровых проверок (по умолчанию 3).
Новое нарушение во время восстановления сбрасывает счётчик восстановлений.
После закрытия новое устойчивое нарушение может создать следующий инцидент.

Snapshot, состояние правила и инцидент сохраняются в одной транзакции. Изменения
блокируют проект, затем runtime state; частичный уникальный индекс дополнительно
ограничивает число открытых инцидентов одним на правило. Подход к блокировкам
соответствует [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
Состояние правил и активные инциденты хранятся в PostgreSQL.

Инцидент сохраняет исходные metric/operator/threshold, severity, требуемое число
нарушений и все четыре сигнала при обнаружении. `startedAt` — timestamp сработавшего
snapshot; `resolvedAt` — timestamp последней требуемой здоровой проверки.
Список возвращается по убыванию `startedAt`, затем id; фильтры status/severity
могут применяться отдельно или вместе. Доступ к чужому правилу/инциденту возвращает `404`.

Поведение при изменении настроек и пробелах в данных:

- Редактирование правила сбрасывает незавершённые последовательности. Открытый
  инцидент сохраняет исходное условие, по которому проверяется его восстановление;
  новые настройки применяются к следующему инциденту. Интервал проверки берётся из текущего правила.
- Отключение правила/мониторинга приостанавливает проверки и сбрасывает счётчики,
  сохраняя открытый инцидент. Повторное включение требует новых последовательных проверок.
- Сбой сбора или новая начальная точка счётчиков сбрасывают последовательности,
  сохраняя открытые инциденты. Недоступность endpoint не считается здоровой проверкой.
- При нулевом traffic значения latency/errors означают отсутствие наблюдений:
  правила по ним пропускаются со сбросом последовательностей, открытый инцидент сохраняется.
  Правила traffic/saturation продолжают проверяться.
- Удаление правила с открытым инцидентом возвращает `409`. После восстановления
  правило можно удалить: runtime state удалится, история инцидентов останется с `ruleId=null`
  и сохранёнными параметрами срабатывания.

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
| `MONITORING_COLLECTION_INTERVAL` | `5s` | Пауза между циклами сбора, минимум 1 с |
| `MONITORING_SCHEDULER_ENABLED` | `true` | Глобальное включение scheduler, независимо от флагов проектов |
| `MONITORING_REQUEST_TIMEOUT` | `5s` | Timeout проверки endpoint, включая тело ответа |
| `MONITORING_MAX_RESPONSE_BYTES` | `2097152` | Максимальный размер ответа endpoint в байтах |
| `MONITORING_RECOVERY_CHECKS` | `3` | Число последовательных здоровых проверок для закрытия инцидента |

Настройки мониторинга типизированы и валидируются при запуске.

## AI-анализ инцидентов (backend)

Новые инциденты автоматически получают анализ в состоянии `PENDING`.
Отдельный Spring scheduler читает очередь из PostgreSQL и обращается к OpenAI
через [Responses API со Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses).
Модель получает сохранённое условие срабатывания, четыре Golden Signals при обнаружении
и реальные snapshots за последние 30 минут до `startedAt`, включая точку обнаружения.
История ограничена 120 равномерно выбранными точками; в контексте явно указано,
была ли она сокращена. Последующие snapshots в контекст не попадают.

Укажите `OPENAI_TOKEN` в `.env` и пересоберите backend:

```sh
docker compose up --build -d --wait backend
```

Compose передаёт токен только backend. Spring при запуске без Docker считывает
переменную окружения `OPENAI_TOKEN`; сам файл `.env` Spring не загружает.
Модель по умолчанию — `gpt-4o-mini`, ответ на русском языке.
Токен, контекст и исходные ошибки OpenAI не выводятся в логи или REST-ответы.
Запросы используют `store=false`; автоматических HTTP retries нет.

| Метод | Путь | Ответ |
|---|---|---|
| GET | `/api/incidents/{incidentId}/ai-analysis` | 200 с текущим состоянием и результатом |
| POST | `/api/incidents/{incidentId}/ai-analysis/retry` | 202 с `PENDING`; повторный POST во время анализа возвращает текущий `PENDING` |

Оба endpoint проверяют владельца проекта. Чужой/несуществующий инцидент возвращает
404. Для инцидента, созданного до этой миграции, GET возвращает 404 до первого Retry.
Повторный запуск доступен для любого завершённого анализа; он очищает предыдущий результат.
Frontend сможет опрашивать GET каждые 3 секунды до завершения `PENDING`.
В Incident Details реализован блок AI Analysis с результатом, состояниями,
Retry и polling каждые 3 секунды только во время `PENDING`. Для старых инцидентов
без анализа доступна кнопка `Analyze incident`.

Состояния результата: `COMPLETED`, `INSUFFICIENT_DATA`, `FAILED`.
Backend проверяет структуру JSON, допустимый статус, типы и размеры полей, наличие
причины, evidence и recommendations при `COMPLETED`. Невалидный ответ, отказ модели,
неполный ответ, HTTP-ошибка или timeout приводят к `FAILED` с безопасным сообщением.
Без токена backend и мониторинг запускаются нормально, а анализ получает `FAILED`.
Retry после настройки токена позволяет повторить запрос.

Миграция V5 создаёт таблицу `ai_analyses` и PostgreSQL-триггер: запись `PENDING`
сохраняется в той же транзакции, что и новый Incident, без изменений Rule Engine.
Worker видит только committed записи; HTTP-запрос выполняется вне транзакции БД.
Отдельные scheduler threads исключают ожидание OpenAI в цикле мониторинга.
Задачи обрабатываются по одной на процесс; `FOR UPDATE SKIP LOCKED` и lease защищают
от параллельной обработки. После сбоя процесса незавершённая задача снова доступна
после истечения lease (timeout запроса + 60 с). ID попытки защищает от записи
запоздавшего результата. При восстановлении после сбоя возможен повторный API-запрос.
Удаление проекта каскадно удаляет анализы. Старые инциденты не отправляются в OpenAI
автоматически при миграции.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `OPENAI_TOKEN` | пусто | API-ключ только для backend |
| `OPENAI_MODEL` | `gpt-4o-mini` | Модель с поддержкой Responses и Structured Outputs |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Адрес API; локальный stub в тестах |
| `OPENAI_MAX_OUTPUT_TOKENS` | `2000` | Максимум токенов ответа |
| `AI_REQUEST_TIMEOUT` | `30s` | Timeout всего HTTP-запроса, включая тело ответа |
| `AI_POLL_INTERVAL` | `1s` | Пауза между задачами очереди |
| `AI_HISTORY_WINDOW` | `30m` | Окно истории до обнаружения |
| `AI_MAX_HISTORY_POINTS` | `120` | Максимум реальных точек в запросе |
| `AI_MAX_RESPONSE_BYTES` | `1048576` | Лимит тела ответа API |
| `AI_SCHEDULER_ENABLED` | `true` | Включение фонового worker независимо от мониторинга |

`./backend/mvnw -f backend/pom.xml verify` проверяет JSON-контракт и HTTP-клиент,
полный AI workflow с PostgreSQL и локальной заглушкой OpenAI, Retry, ownership,
rollback, конкурентные запросы, восстановление lease и независимость scheduler.
Тесты не используют настоящий токен и не обращаются к OpenAI.

Проверка с настоящим OpenAI API, включая Retry и ограничения качества выводов,
описана в [отчёте AI live API](AI_LIVE_API_REPORT.md).
