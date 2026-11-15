package io.github.gomes.decisions.spi;

/** Failure talking to a decision provider (HTTP error, unsupported feature, malformed response). */
public class DecisionException extends RuntimeException {

    private final String provider;
    private final int httpStatus;
    private final String responseBody;

    public DecisionException(String provider, String message) {
        this(provider, message, -1, null, null);
    }

    public DecisionException(String provider, String message, Throwable cause) {
        this(provider, message, -1, null, cause);
    }

    public DecisionException(String provider, String message, int httpStatus, String responseBody, Throwable cause) {
        super("[" + provider + "] " + message, cause);
        this.provider = provider;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public String provider() { return provider; }

    /** HTTP status returned by the provider, or -1 when the failure happened before/outside HTTP. */
    public int httpStatus() { return httpStatus; }

    public String responseBody() { return responseBody; }
}
