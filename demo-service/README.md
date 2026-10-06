# Demo service

Небольшой генератор синтетических метрик для разработки SRE Copilot.
Python 3.10+; внешних зависимостей нет. Генератор работает в фоне без нагрузочного клиента.
Профили меняют тестовые данные, а не фактическую нагрузку CPU или задержку HTTP endpoint.

## Запуск

Из корня репозитория:

```sh
docker compose up --build -d --wait demo-service
```

Или локально:

```sh
python3 demo-service/app.py
# Другой порт и начальный сценарий:
python3 demo-service/app.py --port 8082 --scenario overloaded
```

Адрес по умолчанию — <http://localhost:8081>. Локально сервис слушает `127.0.0.1`,
в контейнере — `0.0.0.0`. Настройки: `HOST`, `PORT`, `DEMO_SCENARIO`;
параметры командной строки имеют приоритет. В Compose внешний порт задаётся `DEMO_SERVICE_PORT`.

| Endpoint | Назначение |
|---|---|
| `GET /` или `GET /scenario` | Текущий сценарий, профили, число сгенерированных запросов |
| `GET /metrics` | Метрики в Prometheus text format 0.0.4 |
| `GET /actuator/prometheus` | Alias `/metrics` для проверки endpoint типа ACTUATOR |
| `GET /health` | Проверка самого генератора; HTTP 200 даже в `unavailable` |
| `POST /scenario` | Переключение сценария: `{"name":"slow"}` |

## Сценарии

Значения приблизительные: задержка и CPU меняются случайно, ошибки выбираются для каждого запроса.

| Сценарий | Запросов/с | Средняя задержка, мс | Ошибки 5xx, % | CPU, % |
|---|---:|---:|---:|---:|
| `normal` | 20 | 80 | 0.5 | 20 |
| `slow` | 20 | 900 | 0.5 | 45 |
| `errors` | 20 | 120 | 25 | 35 |
| `overloaded` | 150 | 650 | 8 | 95 |
| `unavailable` | 0 | — | — | — |

`unavailable` возвращает HTTP 503 на обоих endpoints метрик. Управление сценарием
остаётся доступным, поэтому можно восстановить endpoint без перезапуска контейнера.

```sh
curl http://localhost:8081/metrics
curl -X POST http://localhost:8081/scenario \
  -H 'Content-Type: application/json' -d '{"name":"errors"}'
curl -X POST http://localhost:8081/scenario \
  -H 'Content-Type: application/json' -d '{"name":"unavailable"}'
curl -i http://localhost:8081/metrics
curl -X POST http://localhost:8081/scenario \
  -H 'Content-Type: application/json' -d '{"name":"normal"}'
```

## Исходные метрики для Golden Signals

`http_server_requests_seconds` — histogram с labels `method="GET"`, `uri="/synthetic"`,
`status="200"`/`"500"`. Путь `/synthetic` обозначает искусственные запросы генератора.
Экспортируются накопительные `_count`, `_sum` и `_bucket`, а также gauge `process_cpu_usage`
в диапазоне 0–1 и `demo_uptime_seconds`.

Для будущего адаптера можно использовать два последовательных опроса:

- Traffic: сумма приростов `_count` / время между опросами в секундах.
- Latency: сумма приростов `_sum` / сумма приростов `_count` × 1000 мс.
- Errors: прирост `_count` со `status="500"` / прирост всех `_count` × 100%.
- Saturation: `process_cpu_usage` × 100%.

Счётчики сохраняются при смене сценария и обнуляются при перезапуске приложения.
Сразу после переключения расчёт по интервалу может включать оба сценария.
Для rates нужны два опроса; если прироста запросов нет, latency/errors не определены.
После перезапуска предыдущие значения счётчиков нельзя использовать как обычный прирост.

Формат histogram соответствует [Prometheus exposition format](https://prometheus.io/docs/instrumenting/exposition_formats/).

## Подключение к backend

Для backend в той же Docker Compose сети:

```sh
curl -i -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo service","endpointUrl":"http://demo-service:8081/metrics","endpointType":"PROMETHEUS"}'
# Используйте id из ответа:
curl -X POST http://localhost:8080/api/projects/<id>/test-connection
```

При локальном запуске backend используйте `http://localhost:8081/metrics`.
Если backend в Docker, а генератор запущен на хосте, запустите его с `--host 0.0.0.0`
и используйте `http://host.docker.internal:8081/metrics`.

Сейчас backend проверяет подключение; периодический сбор метрик и Rule Engine
будут реализованы отдельно.
