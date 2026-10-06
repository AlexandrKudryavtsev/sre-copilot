package com.srecopilot.monitoring;

import java.math.BigDecimal;

public record GoldenSignals(BigDecimal latency, BigDecimal traffic, BigDecimal errors, BigDecimal saturation) {
}
