# ТЗ: Интеграция OpenAI API в SRE Copilot

Цель: добавить AI-анализ уже обнаруженных инцидентов. AI должен определять вероятную причину проблемы и предлагать действия для её устранения на основе собранных метрик.

Текущий стек: Spring Boot, React, PostgreSQL, OpenAI API.

## 1. Подключение OpenAI

В `.env` уже находится токен:

```
OPENAI_TOKEN=...
```

Backend должен считывать `OPENAI_TOKEN` из переменных окружения и использовать его для запросов к OpenAI API.

- Все запросы к OpenAI выполняются только через Spring Boot Backend.
- Токен не передаётся во frontend и не записывается в логи.
- Модель и параметры запросов задаются через конфигурацию backend.
- Используется готовая модель OpenAI через API, без обучения собственной модели.

## 2. Запуск AI-анализа

AI-анализ автоматически запускается после создания нового Incident.

```
Monitoring
    ↓
Rule Engine
    ↓
Incident created (OPEN)
    ↓
Save Incident to PostgreSQL
    ↓
Start AI analysis asynchronously
    ↓
OpenAI API
    ↓
Save AI result
    ↓
Display result in Incident Details
```

Важно: AI не участвует в определении инцидентов. Если OpenAI API недоступен, мониторинг и создание Incident продолжают работать.

## 3. Данные, передаваемые модели

Backend формирует структурированный контекст:

```
{
  "incidentId": "INC-1042",
  "project": "Checkout API",
  "severity": "CRITICAL",
  "triggeredRule": {
    "metric": "LATENCY",
    "operator": ">=",
    "threshold": 800,
    "consecutiveViolations": 3
  },
  "metricsAtDetection": {
    "latencyMs": 842,
    "trafficRps": 1248,
    "errorsPercent": 3.24,
    "saturationPercent": 78
  },
  "metricHistory": []
}
```

`metricHistory` должен содержать реальные значения Golden Signals за период до возникновения инцидента (например, последние 30 минут).

## 4. Требования к ответу AI

Модель должна вернуть структурированный JSON:

```
{
  "status": "COMPLETED",
  "probableCause": "Вероятное увеличение нагрузки на сервис.",
  "evidence": [
    "Latency превысила установленный порог.",
    "Saturation достигла 78%."
  ],
  "recommendations": [
    "Проверить загрузку ресурсов приложения.",
    "Проанализировать медленные запросы.",
    "Проверить изменения нагрузки."
  ]
}
```

Требования:

- Не выдумывать факты, которых нет в переданных метриках.
- Формулировать гипотезу, а не гарантированный диагноз.
- Объяснять, на каких данных основан вывод.
- Предлагать конкретные действия для проверки и устранения проблемы.
- Если данных недостаточно, возвращать `INSUFFICIENT_DATA`.

Backend должен валидировать JSON перед сохранением.

## 5. Хранение результатов

Добавить таблицу `ai_analyses`:

| Поле              | Назначение                                       |
| ----------------- | ------------------------------------------------ |
| `id`              | ID анализа                                       |
| `incident_id`     | Связанный Incident                               |
| `status`          | PENDING / COMPLETED / FAILED / INSUFFICIENT_DATA |
| `probable_cause`  | Вероятная причина                                |
| `evidence`        | Подтверждающие данные (JSON)                     |
| `recommendations` | Рекомендации (JSON)                              |
| `error_message`   | Ошибка API                                       |
| `created_at`      | Время создания                                   |
| `completed_at`    | Время завершения                                 |

## 6. Асинхронность и обработка ошибок

При создании Incident сразу устанавливается `PENDING`, после чего анализ выполняется в фоновом процессе Spring Boot.

- Успешный ответ → `COMPLETED`.
- Недостаточно данных → `INSUFFICIENT_DATA`.
- Ошибка API, timeout или некорректный ответ → `FAILED`.
- Пользователь может повторить анализ через кнопку Retry Analysis.
- Ошибка AI не изменяет статус Incident и не останавливает мониторинг.

## 7. REST API

```
GET  /api/incidents/{id}/ai-analysis
POST /api/incidents/{id}/ai-analysis/retry
```

Frontend получает состояние анализа через обычный REST polling (например, каждые 3 секунды), без WebSocket и SSE.

## 8. Интерфейс Incident Details

Добавить блок AI Analysis.

AI Analysis

Completed

Probable cause

Increased application load may be contributing to elevated response latency.

Evidence

- Latency exceeded 800 ms.
- Saturation reached 78%.

Recommended actions

1. Check application resource utilization.
2. Inspect slow requests.
3. Compare current traffic with baseline.

AI-generated suggestions. Verify findings before taking action.

При `PENDING` отображается Analyzing incident…, при `FAILED` — сообщение об ошибке и кнопка Retry Analysis.

## 9. Критерии готовности

Интеграция считается завершённой, когда:

- Новый Incident автоматически запускает анализ через OpenAI API.
- Backend использует `OPENAI_TOKEN` из окружения.
- Модель получает реальные метрики и правило срабатывания.
- Ответ сохраняется в PostgreSQL и отображается в Incident Details.
- Пользователь видит состояния `PENDING`, `COMPLETED`, `FAILED` и `INSUFFICIENT_DATA`.
- При ошибке API доступен повторный запуск.
- Мониторинг и определение инцидентов полностью независимы от OpenAI.

Итог: AI — это отдельный асинхронный модуль, который получает готовый Incident, анализирует его через OpenAI API и сохраняет результат. Существующее ядро мониторинга менять не нужно.