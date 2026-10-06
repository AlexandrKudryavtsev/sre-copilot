# Техническое задание: SRE Copilot — Monitoring Core

## 1. Назначение системы

SRE Copilot — веб-приложение для мониторинга состояния пользовательских сервисов.

Пользователь должен иметь возможность:

1. зарегистрировать в системе проект;
2. указать Prometheus/Actuator endpoint проекта;
3. проверить доступность endpoint;
4. запустить мониторинг проекта;
5. получать четыре Golden Signals;
6. просматривать текущие и исторические значения метрик;
7. создавать правила мониторинга;
8. автоматически определять устойчивое нарушение правила;
9. автоматически создавать Incident;
10. просматривать текущие и исторические Incident.

---

# 2. Архитектура

Для этой части проекта достаточно:

```text
┌─────────────────┐
│ React Frontend  │
└────────┬────────┘
         │
         │ REST/HTTPS
         ▼
┌─────────────────────────────┐
│ Spring Boot Backend         │
│                             │
│ Project Management          │
│ Monitoring                  │
│ Metric Storage              │
│ Rule Engine                 │
│ Incident Management         │
└───────┬───────────┬─────────┘
        │           │
        │           │ HTTP
        ▼           ▼
 ┌────────────┐  ┌────────────────────┐
 │ PostgreSQL │  │ Monitored Service  │
 └────────────┘  │ Prometheus/Actuator│
                 └────────────────────┘
```

Backend — **один модульный Spring Boot монолит**, а не набор микросервисов.

---

# 3. Основные сущности

Для monitoring-core нужны пять основных сущностей:

```text
User
  │
  └── Project
        │
        ├── MetricSnapshot
        │
        ├── MonitoringRule
        │       │
        │       └── MonitoringRuleState
        │
        └── Incident
```

То есть в PostgreSQL:

```text
users
projects
metric_snapshots
monitoring_rules
monitoring_rule_states
incidents
```

---

# 4. Project

`Project` представляет подключённое к мониторингу приложение.

Минимальная модель:

```text
Project

id
userId

name
description

endpointUrl
endpointType

monitoringEnabled
connectionStatus

createdAt
updatedAt
```

### endpointType

Для MVP:

```text
PROMETHEUS
ACTUATOR
```

При этом Spring Boot Actuator `/actuator/prometheus` фактически предоставляет Prometheus-compatible endpoint, поэтому внутри реализации можно использовать общий механизм получения Prometheus-метрик.

### connectionStatus

```text
CONNECTED
UNAVAILABLE
UNKNOWN
```

---

# 5. Создание проекта

Пользователь открывает:

```text
Projects
    ↓
New Project
```

и заполняет:

```text
Project name *
Description

Monitoring endpoint *
http://localhost:8081/actuator/prometheus
```

Также должна присутствовать операция:

```text
Test connection
```

Backend выполняет запрос к указанному endpoint и проверяет, что источник доступен и возвращает пригодные для обработки метрики.

При успехе:

```text
CONNECTED
```

При ошибке:

```text
UNAVAILABLE
```

---

# 6. Управление проектами

Пользователь должен иметь возможность:

```text
создать Project
получить список Projects
получить один Project
изменить Project
удалить Project
проверить endpoint
включить/выключить monitoring
```

REST API:

```text
GET    /api/projects
POST   /api/projects

GET    /api/projects/{id}
PUT    /api/projects/{id}
DELETE /api/projects/{id}

POST   /api/projects/{id}/test-connection
POST   /api/projects/{id}/monitoring/enable
POST   /api/projects/{id}/monitoring/disable
```

---

# 7. Monitoring

Для каждого проекта с:

```text
monitoringEnabled = true
```

backend периодически получает данные из:

```text
project.endpointUrl
```

Monitoring выполняется автоматически, без участия пользователя.

Общий цикл:

```text
Scheduler
   ↓
найти проекты с monitoringEnabled=true
   ↓
HTTP GET endpoint
   ↓
получить metrics
   ↓
извлечь необходимые значения
   ↓
вычислить Golden Signals
   ↓
создать MetricSnapshot
   ↓
сохранить PostgreSQL
   ↓
передать Snapshot в Rule Engine
```

---

# 8. Golden Signals

Система должна получать четыре показателя:

```text
Latency
Traffic
Errors
Saturation
```

### Latency

Характеризует время обработки запросов.

Единица отображения:

```text
ms
```

Например:

```text
Latency: 842 ms
```

### Traffic

Характеризует интенсивность запросов.

Единица:

```text
requests / second
```

Например:

```text
Traffic: 1248 req/s
```

### Errors

Доля ошибочных запросов.

```text
%
```

Например:

```text
Errors: 3.24%
```

### Saturation

Загрузка ограниченного ресурса приложения.

```text
%
```

Например:

```text
Saturation: 78%
```

**Важно:** практические требуют эти четыре нормализованных показателя, но не фиксируют конкретные PromQL-формулы/имена исходных метрик для их вычисления. Поэтому формулы получения Golden Signals нужно определить отдельно при реализации конкретного Prometheus/Actuator адаптера.

---

# 9. MetricSnapshot

После каждой успешной проверки создаётся snapshot.

```text
MetricSnapshot

id
projectId

timestamp

latency
traffic
errors
saturation
```

Пример:

```json
{
  "timestamp": "2026-10-06T13:42:30Z",
  "latency": 842,
  "traffic": 1248,
  "errors": 3.24,
  "saturation": 78
}
```

Snapshots сохраняются в PostgreSQL.

Они нужны одновременно для:

```text
Dashboard
Historical charts
Rule evaluation
Incident context
```

---

# 10. Частота сбора метрик

Monitoring должен выполняться периодически.

Например:

```text
every 30 seconds
```

Но конкретное глобальное значение интервала в практиках не зафиксировано.

Поэтому его следует сделать конфигурационным:

```yaml
monitoring:
  collection-interval: 30s
```

---

# 11. Ошибка получения метрик

Если endpoint временно недоступен:

```text
Scheduler
 ↓
GET endpoint
 ↓
timeout / connection refused / invalid response
 ↓
MetricSnapshot НЕ создаётся
 ↓
ошибка записывается в log
 ↓
Project.connectionStatus = UNAVAILABLE
```

Monitoring Scheduler продолжает работать.

На следующем цикле система снова пытается получить данные.

После успешного запроса:

```text
connectionStatus = CONNECTED
```

Недоступность одного проекта не должна останавливать monitoring остальных проектов.

---

# 12. Dashboard проекта

Project Overview должен отображать текущее состояние:

```text
Checkout API
● Monitoring active

Latency        Traffic        Errors        Saturation
842 ms         1248 req/s     3.24%         78%
```

Кроме текущего значения должны отображаться исторические графики:

```text
Latency trend
Traffic trend
Errors trend
Saturation trend
```

---

# 13. API метрик

Минимально:

```text
GET /api/projects/{projectId}/metrics/current

GET /api/projects/{projectId}/metrics/history
```

Например:

```text
GET /api/projects/12/metrics/history?from=...&to=...
```

`current` возвращает последний `MetricSnapshot`.

`history` — snapshots за указанный период.

---

# 14. MonitoringRule

Пользователь должен иметь возможность определить условия, при которых состояние сервиса считается проблемным.

Сущность:

```text
MonitoringRule

id
projectId

name

metric
operator
threshold

severity

checkInterval
consecutiveViolations

enabled

createdAt
updatedAt
```

Именно **threshold + severity + check interval + количество последовательных нарушений** были предусмотрены исходными требованиями.

---

# 15. Поддерживаемые метрики правил

```text
LATENCY
TRAFFIC
ERRORS
SATURATION
```

Например:

```text
metric = LATENCY
```

---

# 16. Операторы

Для MVP достаточно:

```text
GREATER_THAN
GREATER_OR_EQUAL

LESS_THAN
LESS_OR_EQUAL
```

Например:

```text
LATENCY >= 800 ms
```

или:

```text
ERRORS >= 5%
```

---

# 17. Severity

Минимальный вариант:

```text
WARNING
CRITICAL
```

Пример:

```text
High latency

metric: LATENCY
operator: GREATER_OR_EQUAL
threshold: 800
severity: CRITICAL
```

Severity переносится в созданный Incident.

---

# 18. Consecutive violations

Это ключевая часть определения Incident.

Rule содержит:

```text
consecutiveViolations = N
```

Например:

```text
Latency >= 800 ms
3 consecutive violations
```

Incident создаётся **не после первого превышения**, а только после трёх последовательных нарушений.

---

# 19. MonitoringRuleState

Чтобы корректно считать нарушения между monitoring cycles, используется отдельное runtime-состояние:

```text
MonitoringRuleState

ruleId

state

consecutiveViolations
consecutiveRecoveries

activeIncidentId

lastCheckedAt
lastViolationAt
```

### state

```text
HEALTHY
VIOLATING
INCIDENT_OPEN
```

Начальное состояние:

```text
HEALTHY
consecutiveViolations = 0
```

---

# 20. Проверка Rule

Допустим:

```text
Latency >= 800 ms
consecutiveViolations = 3
```

Последовательность:

```text
700 ms
↓
HEALTHY
counter = 0


830 ms
↓
VIOLATING
counter = 1


910 ms
↓
VIOLATING
counter = 2


850 ms
↓
counter = 3
↓
CREATE INCIDENT
↓
INCIDENT_OPEN
```

---

# 21. Сброс последовательности

Если между нарушениями метрика возвращается в нормальное состояние:

```text
830 ms → violation #1

910 ms → violation #2

650 ms → healthy
```

то:

```text
consecutiveViolations = 0
state = HEALTHY
```

Следующее превышение снова считается первым:

```text
850 ms → violation #1
```

---

# 22. Защита от дублирования Incident

После создания Incident:

```text
state = INCIDENT_OPEN
activeIncidentId = созданный Incident
```

Если нарушение продолжается:

```text
900 ms
920 ms
950 ms
980 ms
```

новые Incident **не создаются**.

То есть:

```text
INCIDENT_OPEN
      +
rule still violated
      ↓
keep existing Incident
```

---

# 23. Определение Incident

Таким образом, точное условие возникновения Incident:

> Incident создаётся в момент, когда активное MonitoringRule нарушено требуемое количество последовательных проверок, причём для данного Rule в этот момент отсутствует уже открытый Incident.

Формально:

```text
rule.enabled
AND
violation(metric, rule) == true
AND
consecutiveViolations >= requiredViolations
AND
activeIncidentId == null
```

тогда:

```text
CREATE INCIDENT
```

---

# 24. Incident

Минимальная модель:

```text
Incident

id
projectId
ruleId

title

status
severity

startedAt
resolvedAt

triggerMetric
triggerValue

ruleMetric
ruleOperator
ruleThreshold
requiredConsecutiveViolations

createdAt
updatedAt
```

---

# 25. Snapshot правила

В Incident следует сохранить значения MonitoringRule на момент срабатывания.

Например:

```text
ruleMetric = LATENCY
ruleOperator = GREATER_OR_EQUAL
ruleThreshold = 800
requiredConsecutiveViolations = 3
severity = CRITICAL
```

Даже если позже пользователь изменит Rule:

```text
800 ms → 1000 ms
```

старый Incident всё равно должен отображать:

```text
Triggered rule:
Latency >= 800 ms
for 3 consecutive checks
```

---

# 26. Значения метрик при Incident

Кроме `triggerValue`, я рекомендую сохранить snapshot всех четырёх сигналов на момент обнаружения:

```text
latencyAtDetection
trafficAtDetection
errorsAtDetection
saturationAtDetection
```

Тогда Incident будет содержать:

```text
INC-1042
Elevated response latency

Critical
Open

Triggered:
Latency >= 800 ms × 3

Metrics at detection:

Latency       842 ms
Traffic       1248 req/s
Errors        3.24%
Saturation    78%
```

---

# 27. Incident lifecycle

Здесь исходные практики уже не определяют полностью механизм восстановления, поэтому это нужно считать **уточнением ТЗ**.

Для проекта я предлагаю:

```text
OPEN
RESOLVED
```

После создания:

```text
status = OPEN
```

Чтобы избежать мгновенного закрытия из-за одного хорошего измерения, используем симметричный механизм восстановления.

Например:

```text
3 violations → OPEN

3 healthy checks → RESOLVED
```

Количество последовательных восстановлений можно сделать конфигурационным:

```text
recoveryChecks = 3
```

---

# 28. Автоматическое восстановление

Пример:

```text
threshold = 800 ms

850 → OPEN
910 → OPEN
870 → OPEN

760 → recovery #1
740 → recovery #2
720 → recovery #3
          ↓
       RESOLVED
```

После этого:

```text
Incident.status = RESOLVED
Incident.resolvedAt = now()

RuleState.state = HEALTHY
RuleState.activeIncidentId = null

consecutiveViolations = 0
consecutiveRecoveries = 0
```

Теперь новое устойчивое нарушение может создать **новый Incident**.

---

# 29. Полная state machine

Получается:

```text
                   violation
              ┌─────────────────┐
              │                 ▼
         ┌─────────┐       ┌───────────┐
         │ HEALTHY │──────►│ VIOLATING │
         └─────────┘       └─────┬─────┘
              ▲                  │
              │ healthy          │ N violations
              │                  ▼
              │           ┌───────────────┐
              │           │ INCIDENT_OPEN │
              │           └───────┬───────┘
              │                   │
              │          N healthy checks
              │                   │
              └───────────────────┘
```

---

# 30. Rule Engine

Логически алгоритм выглядит так:

```text
MetricSnapshot received
        ↓
find enabled rules for project
        ↓
for each Rule
        ↓
select corresponding metric
        ↓
compare metric with threshold
        ↓
update MonitoringRuleState
        ↓
if threshold reached
        ↓
create Incident
```

Псевдокод:

```text
if rule violated:

    recoveries = 0

    if state == INCIDENT_OPEN:
        return

    violations++

    if violations >= requiredViolations:
        createIncident()
        state = INCIDENT_OPEN
    else:
        state = VIOLATING

else:

    violations = 0

    if state == INCIDENT_OPEN:
        recoveries++

        if recoveries >= recoveryChecks:
            resolveIncident()
            state = HEALTHY
    else:
        state = HEALTHY
```

---

# 31. Транзакционность

Обновление:

```text
MonitoringRuleState
+
создание Incident
```

должно выполняться в одной транзакции.

Иначе два параллельных monitoring cycle теоретически могут одновременно решить:

```text
counter == 3
```

и создать два Incident.

На уровне Spring:

```text
@Transactional
evaluateRule(...)
```

Для строки `MonitoringRuleState` желательно использовать блокировку при изменении состояния.

---

# 32. Incidents API

Минимально:

```text
GET /api/projects/{projectId}/incidents
GET /api/incidents/{incidentId}
```

Дополнительно можно:

```text
GET /api/projects/{projectId}/incidents?status=OPEN

GET /api/projects/{projectId}/incidents?severity=CRITICAL
```

---

# 33. Incidents page

Страница:

```text
Incidents
```

показывает:

| Incident | Status | Severity | Triggered rule | Started |
|---|---|---|---|---|
| INC-1042 | Open | Critical | Latency ≥ 800 ms × 3 | 8 min ago |
| INC-1041 | Resolved | Warning | Errors ≥ 5% × 3 | 2 h ago |

Клик по строке:

```text
Incidents
    ↓
Incident Details
```

---

# 34. Incident Details

Без AI этот экран должен содержать три основных блока.

**Incident summary:**

```text
INC-1042
Elevated response latency

OPEN
CRITICAL

Started:
13:42

Duration:
18 min
```

**Triggered rule:**

```text
High latency

Latency >= 800 ms
3 consecutive checks

Trigger value:
842 ms
```

**Metrics:**

```text
Latency       842 ms
Traffic       1248 req/s
Errors        3.24%
Saturation    78%
```

И ниже — графики Golden Signals за период вокруг Incident.

---

# 35. Monitoring Rules UI

Страница:

```text
Monitoring Rules
```

Таблица:

| Rule | Signal | Condition | Severity | Status |
|---|---|---|---|---|
| High latency | Latency | ≥ 800 ms × 3 | Critical | Enabled |
| Error spike | Errors | ≥ 5% × 3 | Critical | Enabled |

Операции:

```text
Create
Edit
Enable
Disable
Delete
```

---

# 36. Create Rule UI

Форма:

```text
Rule name
[High latency]

Metric
[Latency ▼]

Operator
[>= ▼]

Threshold
[800]

Severity
[Critical ▼]

Check interval
[30 sec ▼]

Consecutive violations
[3]

[Create rule]
```

---

# 37. Что не входит в это ТЗ

На этом этапе **вообще не реализуем**:

```text
LLM
AI Analysis
AI status
AI recommendations
AI probable cause
AI retry
LLM API
prompt engineering
structured AI output
AI polling
```

Также пока не нужны:

```text
Kafka / RabbitMQ
SSE
WebSocket
microservices
Kubernetes monitoring
logs
distributed tracing
automatic remediation
Slack / Telegram / email notifications
```

---

# 38. Минимальная структура backend

Я бы организовал Spring Boot примерно так:

```text
src/main/java/.../srecopilot/

    project/
        Project
        ProjectRepository
        ProjectService
        ProjectController

    monitoring/
        MonitoringScheduler
        MetricsCollector
        PrometheusClient
        GoldenSignalsCalculator

    metric/
        MetricSnapshot
        MetricSnapshotRepository
        MetricService
        MetricController

    rule/
        MonitoringRule
        MonitoringRuleState
        MonitoringRuleRepository
        MonitoringRuleStateRepository
        RuleEngine
        RuleService
        RuleController

    incident/
        Incident
        IncidentRepository
        IncidentService
        IncidentController

    common/
        exception/
        config/
```

---

# 39. Полный основной сценарий

В итоге всё ядро проекта сводится к следующему:

```text
Пользователь
    ↓
создаёт Project
    ↓
указывает /actuator/prometheus
    ↓
Test connection
    ↓
CONNECTED
    ↓
включает Monitoring
    ↓

────────────────────────────────

Scheduler
    ↓
GET /actuator/prometheus
    ↓
рассчитать Golden Signals
    ↓
Latency
Traffic
Errors
Saturation
    ↓
сохранить MetricSnapshot
    ↓
Rule Engine
    ↓
проверить MonitoringRules
    ↓

────────────────────────────────

Latency >= 800?
    ↓ YES
counter = 1

следующая проверка
    ↓
Latency >= 800?
    ↓ YES
counter = 2

следующая проверка
    ↓
Latency >= 800?
    ↓ YES
counter = 3
    ↓
CREATE INCIDENT
    ↓
status = OPEN
    ↓
показать в Overview / Incidents

────────────────────────────────

нарушение продолжается
    ↓
новый Incident НЕ создаётся

────────────────────────────────

3 нормальных измерения
    ↓
Incident = RESOLVED
    ↓
RuleState = HEALTHY

────────────────────────────────

следующее устойчивое нарушение
    ↓
может создать новый Incident
```

# 40. Definition of Done этой части

**Monitoring Core можно считать законченным**, когда работает полный end-to-end сценарий:

```text
Create Project
      ↓
Connect real Actuator/Prometheus endpoint
      ↓
Collect real metrics
      ↓
Calculate 4 Golden Signals
      ↓
Store MetricSnapshots
      ↓
Display Dashboard
      ↓
Create Monitoring Rule
      ↓
Detect consecutive violations
      ↓
Automatically create Incident
      ↓
Do not duplicate Incident
      ↓
Display Incident + trigger + metric history
      ↓
Detect recovery
      ↓
Resolve Incident
```
