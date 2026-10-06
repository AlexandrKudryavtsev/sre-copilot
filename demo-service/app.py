"""Synthetic Prometheus endpoint for local monitoring development."""

import argparse
import json
import os
import random
import threading
import time
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


@dataclass(frozen=True)
class Profile:
    requests_per_second: float
    latency_ms: float
    error_percent: float
    cpu_usage: float


PROFILES = {
    "normal": Profile(20, 80, 0.5, 0.2),
    "slow": Profile(20, 900, 0.5, 0.45),
    "errors": Profile(20, 120, 25, 0.35),
    "overloaded": Profile(150, 650, 8, 0.95),
    "unavailable": Profile(0, 0, 0, 0),
}
BUCKETS = (0.05, 0.1, 0.25, 0.5, 1, 2, 5, float("inf"))


class Simulation:
    def __init__(self, scenario):
        self.lock = threading.Lock()
        self.started_at = time.monotonic()
        self.updated_at = self.started_at
        self.scenario = scenario
        self.pending_requests = 0.0
        self.cpu_usage = PROFILES[scenario].cpu_usage
        self.counts = {status: 0 for status in ("200", "500")}
        self.sums = {status: 0.0 for status in self.counts}
        self.buckets = {status: [0] * len(BUCKETS) for status in self.counts}

    def _advance(self):
        """Use elapsed time, so scraping frequency does not generate traffic."""
        now = time.monotonic()
        elapsed = now - self.updated_at
        self.updated_at = now
        profile = PROFILES[self.scenario]
        self.pending_requests += elapsed * profile.requests_per_second
        count = int(self.pending_requests)
        self.pending_requests -= count
        for _ in range(count):
            status = "500" if random.random() * 100 < profile.error_percent else "200"
            duration = max(0.001, random.gauss(profile.latency_ms, profile.latency_ms * 0.2) / 1000)
            self.counts[status] += 1
            self.sums[status] += duration
            for index, bound in enumerate(BUCKETS):
                if duration <= bound:
                    self.buckets[status][index] += 1
        self.cpu_usage = min(1.0, max(0.0, profile.cpu_usage + random.uniform(-0.02, 0.02)))

    def set_scenario(self, name):
        with self.lock:
            self._advance()  # Attribute preceding traffic to the preceding scenario.
            self.scenario = name
            self.cpu_usage = PROFILES[name].cpu_usage

    def status(self):
        with self.lock:
            self._advance()
            return {
                "scenario": self.scenario,
                "profile": asdict(PROFILES[self.scenario]),
                "profiles": {name: asdict(profile) for name, profile in PROFILES.items()},
                "uptime_seconds": round(time.monotonic() - self.started_at, 2),
                "generated_requests": sum(self.counts.values()),
                "metrics_available": self.scenario != "unavailable",
                "endpoints": {
                    "metrics": "/metrics",
                    "actuator_alias": "/actuator/prometheus",
                    "health": "/health",
                    "scenario": "GET /scenario, POST /scenario with {\"name\": \"normal\"}",
                },
            }

    def metrics(self):
        with self.lock:
            self._advance()
            if self.scenario == "unavailable":
                return None
            lines = [
                "# HELP http_server_requests_seconds Synthetic HTTP request duration in seconds.",
                "# TYPE http_server_requests_seconds histogram",
            ]
            for status in self.counts:
                labels = f'method="GET",uri="/synthetic",status="{status}"'
                for bound, count in zip(BUCKETS, self.buckets[status]):
                    le = "+Inf" if bound == float("inf") else str(bound)
                    lines.append(f'http_server_requests_seconds_bucket{{{labels},le="{le}"}} {count}')
                lines.append(f"http_server_requests_seconds_sum{{{labels}}} {self.sums[status]:.9f}")
                lines.append(f"http_server_requests_seconds_count{{{labels}}} {self.counts[status]}")
            lines.extend([
                "# HELP process_cpu_usage Synthetic CPU usage, between 0 and 1.",
                "# TYPE process_cpu_usage gauge",
                f"process_cpu_usage {self.cpu_usage:.6f}",
                "# HELP demo_uptime_seconds Time since the simulator started.",
                "# TYPE demo_uptime_seconds gauge",
                f"demo_uptime_seconds {time.monotonic() - self.started_at:.3f}",
            ])
            return "\n".join(lines) + "\n"


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, body, content_type):
        payload = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def json_response(self, status, body):
        self.respond(status, json.dumps(body, indent=2) + "\n", "application/json; charset=utf-8")

    def do_GET(self):
        path = urlsplit(self.path).path
        if path in ("/metrics", "/actuator/prometheus"):
            metrics = self.server.simulation.metrics()
            self.respond(503 if metrics is None else 200,
                         "Metrics endpoint temporarily unavailable.\n" if metrics is None else metrics,
                         "text/plain; version=0.0.4; charset=utf-8")
        elif path == "/health":
            self.json_response(200, {"status": "ok"})
        elif path in ("/", "/scenario"):
            self.json_response(200, self.server.simulation.status())
        else:
            self.json_response(404, {"error": "Unknown endpoint."})

    def do_POST(self):
        if urlsplit(self.path).path != "/scenario":
            self.json_response(404, {"error": "Unknown endpoint."})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length > 1024:
                self.json_response(413, {"error": "Request body exceeds 1024 bytes."})
                return
            if length <= 0:
                raise ValueError()
            body = json.loads(self.rfile.read(length))
            name = body.get("name") if isinstance(body, dict) else None
            if not isinstance(name, str) or name not in PROFILES:
                raise ValueError()
        except (ValueError, UnicodeDecodeError):
            self.json_response(400, {"error": 'Expected {"name": "scenario"}.', "scenarios": list(PROFILES)})
            return
        self.server.simulation.set_scenario(name)
        self.json_response(200, self.server.simulation.status())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.getenv("HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=os.getenv("PORT", "8081"))
    parser.add_argument("--scenario", choices=PROFILES, default=os.getenv("DEMO_SCENARIO", "normal"))
    args = parser.parse_args()
    if args.scenario not in PROFILES:
        parser.error("DEMO_SCENARIO must be one of: " + ", ".join(PROFILES))
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.simulation = Simulation(args.scenario)
    # Tick independently of incoming requests; reads also catch up to the current time.
    stopped = threading.Event()

    def generate():
        while not stopped.wait(1):
            with server.simulation.lock:
                server.simulation._advance()

    generator = threading.Thread(target=generate, daemon=True)
    generator.start()
    print(f"Demo service listening on http://{args.host}:{args.port}; scenario={args.scenario}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        stopped.set()
        server.server_close()
        generator.join()


if __name__ == "__main__":
    main()
