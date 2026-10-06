package com.srecopilot.monitoring;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrometheusTextValidatorTest {

    private final PrometheusTextValidator validator = new PrometheusTextValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "requests_total 42\n",
            "# HELP requests_total Requests\n# TYPE requests_total counter\nrequests_total 42\n",
            "request_seconds_bucket{le=\"+Inf\"} 3\nrequest_seconds_sum 1.2e-3\n",
            "sample{method=\"GET\",path=\"/a,b#c\",} -0.25 1700000000000\n",
            "sample{value=\"escaped\\\"quote\\nline\\\\slash\"} NaN\n",
            "sample{} +Inf\nother -Inf\n",
            "\t:sample_name { key = \"значение\" }\t.5\t-123\n",
            "\r\nsample 1\r\n"
    })
    void acceptsCommonPrometheusSamples(String text) {
        assertThatCode(() -> validator.validate(text)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "\n", "# HELP sample No data\n", "sample 1",
            "<html>healthy</html>\n", "{\"status\":\"UP\"}\n",
            "sample not-a-number\n", "sample 1\nbroken response\n",
            "sample{key=\"unterminated} 1\n", "sample{key=not-quoted} 1\n",
            "sample{key=\"a\",key=\"b\"} 1\n", "sample{key=\"bad\\t\"} 1\n",
            "sample 1 extra tokens\n", "sample 1 9223372036854775808\n",
            "sample{key=\"a\",,} 1\n", "sample{}1\n", "sample 1e9999\n"
    })
    void rejectsEmptyOrMalformedExposition(String text) {
        assertThatThrownBy(() -> validator.validate(text)).isInstanceOf(IllegalArgumentException.class);
    }
}
