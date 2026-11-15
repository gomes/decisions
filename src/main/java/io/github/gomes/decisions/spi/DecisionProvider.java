package io.github.gomes.decisions.spi;

import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.DecisionResult;

/** A backend that answers typed questions about an input. */
public interface DecisionProvider {

    /** Short stable id, e.g. "openai", "gemini", "typesafe". */
    String id();

    /** Human-readable name. */
    String displayName();

    /** Model used when the request does not set one. */
    String defaultModel();

    Capabilities capabilities();

    DecisionResult decide(DecisionRequest request);

    /** Rejects requests this provider cannot serve before any network call. */
    default void validate(DecisionRequest request) {
        Capabilities c = capabilities();
        if (request.questions().size() > c.maxQuestions()) {
            throw new DecisionException(id(), "too many questions: " + request.questions().size()
                    + " (max " + c.maxQuestions() + ")");
        }
        if (request.hasImages() && !c.images()) {
            throw new DecisionException(id(), "this provider does not accept image input");
        }
    }
}
