package com.srecopilot.project;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpEndpointValidator implements ConstraintValidator<HttpEndpoint, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // @NotBlank handles missing values.
        }
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                    && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
