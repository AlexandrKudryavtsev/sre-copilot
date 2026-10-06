package com.srecopilot.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Parses sample syntax for the Prometheus text format requested by the client. */
@Component
public class PrometheusTextValidator {

    private static final Pattern METRIC_NAME = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private static final Pattern LABEL_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern VALUE = Pattern.compile(
            "(?:[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|NaN|[+-]?Inf)");

    public void validate(String text) {
        parse(text);
    }

    public List<PrometheusSample> parse(String text) {
        if (text.isBlank() || !text.endsWith("\n")) {
            throw new IllegalArgumentException("Endpoint must return non-empty, newline-terminated Prometheus metrics.");
        }
        List<PrometheusSample> samples = new ArrayList<>();
        Set<Series> series = new HashSet<>();
        int lineNumber = 0;
        for (String rawLine : text.split("\n", -1)) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                PrometheusSample sample = parseSample(new Cursor(line));
                if (!series.add(new Series(sample.name(), sample.labels()))) {
                    throw new IllegalArgumentException();
                }
                samples.add(sample);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid Prometheus metric sample at line " + lineNumber + ".");
            }
        }
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Endpoint does not expose any Prometheus metric samples.");
        }
        return List.copyOf(samples);
    }

    private record Series(String name, Map<String, String> labels) {
    }

    private PrometheusSample parseSample(Cursor cursor) {
        String name = cursor.identifier(METRIC_NAME);
        Map<String, String> labels = new HashMap<>();
        if (cursor.peek('{')) {
            cursor.take('{');
            while (!cursor.peek('}')) {
                String label = cursor.identifier(LABEL_NAME);
                if (labels.containsKey(label)) {
                    throw new IllegalArgumentException();
                }
                cursor.take('=');
                labels.put(label, cursor.quotedValue());
                if (!cursor.peek('}')) {
                    cursor.take(',');
                }
            }
            cursor.take('}');
        }
        cursor.requireWhitespace();
        String value = cursor.token();
        if (!VALUE.matcher(value).matches()
                || (!value.endsWith("Inf") && !value.equals("NaN") && !Double.isFinite(Double.parseDouble(value)))) {
            throw new IllegalArgumentException();
        }
        if (!cursor.finished()) {
            cursor.requireWhitespace();
            String timestamp = cursor.token();
            if (!timestamp.matches("[+-]?[0-9]+")) {
                throw new IllegalArgumentException();
            }
            Long.parseLong(timestamp);
        }
        if (!cursor.finished()) {
            throw new IllegalArgumentException();
        }
        return new PrometheusSample(name, labels, value);
    }

    private static final class Cursor {

        private final String line;
        private int position;

        private Cursor(String line) {
            this.line = line;
        }

        private boolean peek(char character) {
            int next = position;
            while (next < line.length() && whitespace(line.charAt(next))) {
                next++;
            }
            return next < line.length() && line.charAt(next) == character;
        }

        private void take(char character) {
            skipWhitespace();
            if (position >= line.length() || line.charAt(position++) != character) {
                throw new IllegalArgumentException();
            }
        }

        private String identifier(Pattern pattern) {
            skipWhitespace();
            Matcher matcher = pattern.matcher(line).region(position, line.length());
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException();
            }
            position = matcher.end();
            return matcher.group();
        }

        private String quotedValue() {
            take('"');
            StringBuilder value = new StringBuilder();
            while (position < line.length()) {
                char character = line.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    if (position >= line.length() || "\\\"n".indexOf(line.charAt(position)) < 0) {
                        throw new IllegalArgumentException();
                    }
                    character = line.charAt(position++);
                    if (character == 'n') {
                        character = '\n';
                    }
                }
                value.append(character);
            }
            throw new IllegalArgumentException();
        }

        private String token() {
            int start = position;
            while (position < line.length() && !whitespace(line.charAt(position))) {
                position++;
            }
            return line.substring(start, position);
        }

        private void requireWhitespace() {
            int before = position;
            skipWhitespace();
            if (before == position) {
                throw new IllegalArgumentException();
            }
        }

        private void skipWhitespace() {
            while (position < line.length() && whitespace(line.charAt(position))) {
                position++;
            }
        }

        private boolean finished() {
            return position == line.length();
        }

        private static boolean whitespace(char character) {
            return character == ' ' || character == '\t';
        }
    }
}
