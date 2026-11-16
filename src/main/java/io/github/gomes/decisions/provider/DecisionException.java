package io.github.gomes.decisions.provider;

/** Failure talking to a decision provider (HTTP error, unsupported feature, malformed response). */
public class DecisionException extends RuntimeException {

    private final String provider;
    private final int upstreamStatus;
    private final String responseBody;

    public DecisionException(String provider, String message) {
        this(provider, message, -1, null, null);
    }

    public DecisionException(String provider, String message, Throwable cause) {
        this(provider, message, -1, null, cause);
    }

    public DecisionException(String provider, String message, int upstreamStatus, String responseBody,
                             Throwable cause) {
        super("[" + provider + "] " + message, cause);
        this.provider = provider;
        this.upstreamStatus = upstreamStatus;
        this.responseBody = responseBody;
    }

    public String provider() { return provider; }

    /** HTTP status returned by the provider, or -1 when the failure happened before/outside HTTP. */
    public int upstreamStatus() { return upstreamStatus; }

    public boolean isUpstreamError() { return upstreamStatus > 0; }

    public String responseBody() { return responseBody; }
}
