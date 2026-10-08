package com.srecopilot.common.exception;

import com.srecopilot.project.ProjectNotFoundException;
import com.srecopilot.rule.RuleNotFoundException;
import com.srecopilot.rule.RuleHasOpenIncidentException;
import com.srecopilot.incident.IncidentNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RuleNotFoundException.class)
    ProblemDetail handleRuleNotFound(RuleNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Monitoring rule not found");
        return problem;
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    ProblemDetail handleIncidentNotFound(IncidentNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Incident not found");
        return problem;
    }

    @ExceptionHandler(RuleHasOpenIncidentException.class)
    ProblemDetail handleRuleConflict(RuleHasOpenIncidentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Monitoring rule has an open incident");
        return problem;
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ProblemDetail handleProjectNotFound(ProjectNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Project not found");
        return problem;
    }

    @ExceptionHandler(FeatureNotImplementedException.class)
    ProblemDetail handleNotImplemented(FeatureNotImplementedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_IMPLEMENTED, exception.getMessage());
        problem.setTitle("Feature not implemented");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, "Request validation failed.");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
