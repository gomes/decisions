package io.github.gomes.decisions.web.api;

import io.github.gomes.decisions.provider.DecisionException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Maps errors of the REST API to RFC 9457 problem details. */
@RestControllerAdvice(assignableTypes = DecisionApiController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail providerNotFound(NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND, "Provider not enabled", e.getMessage());
    }

    /** 502 when the provider API answered with an error; 422 when the provider cannot serve the request. */
    @ExceptionHandler(DecisionException.class)
    ProblemDetail decisionFailed(DecisionException e) {
        if (e.isUpstreamError()) {
            ProblemDetail pd = problem(HttpStatus.BAD_GATEWAY, "Provider error", e.getMessage());
            pd.setProperty("upstreamStatus", e.upstreamStatus());
            pd.setProperty("provider", e.provider());
            return pd;
        }
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Request not supported by provider", e.getMessage());
        pd.setProperty("provider", e.provider());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
        pd.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> Map.of("field", fieldName(v.getPropertyPath().toString()), "message", v.getMessage()))
                .toList());
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(err -> Map.of(
                        "field", err instanceof org.springframework.validation.FieldError fe ? fieldName(fe.getField())
                                : err.getObjectName(),
                        "message", String.valueOf(err.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "Malformed JSON", rootMessage(ex)));
    }

    /** Maps the synthetic {@code @AssertTrue} properties of the domain model to the field they are about. */
    static String fieldName(String path) {
        return path
                .replaceAll("(optionsConsistentWithType|optionValuesUnique)$", "options")
                .replaceAll("criteriaOnlyForYesNo$", "trueWhen")
                .replaceAll("^(?:.*\\.)?inputPresent$", "input")
                .replaceAll("^(?:.*\\.)?questionNamesUnique$", "questions");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }

    /** Surfaces the real reason (e.g. "unknown question type: maybe") instead of Jackson's wrapper text. */
    static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
            if (root instanceof IllegalArgumentException) {
                return root.getMessage();
            }
        }
        String msg = root.getMessage();
        return msg == null ? t.getMessage() : msg.lines().findFirst().orElse(msg);
    }
}
