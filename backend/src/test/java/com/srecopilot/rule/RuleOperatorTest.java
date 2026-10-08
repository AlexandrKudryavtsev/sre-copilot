package com.srecopilot.rule;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleOperatorTest {

    @ParameterizedTest
    @CsvSource({
            "GREATER_THAN,799,false", "GREATER_THAN,800.00,false", "GREATER_THAN,801,true",
            "GREATER_OR_EQUAL,799,false", "GREATER_OR_EQUAL,800.00,true", "GREATER_OR_EQUAL,801,true",
            "LESS_THAN,799,true", "LESS_THAN,800.00,false", "LESS_THAN,801,false",
            "LESS_OR_EQUAL,799,true", "LESS_OR_EQUAL,800.00,true", "LESS_OR_EQUAL,801,false"
    })
    void comparesThresholdsIncludingEqualityAndDifferentDecimalScales(RuleOperator operator, String value, boolean expected) {
        assertThat(operator.matches(new BigDecimal(value), new BigDecimal("800"))).isEqualTo(expected);
    }
}
