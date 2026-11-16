package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;

/** A backend that answers typed questions about an input. */
public interface DecisionProvider {

    /** Short stable id, e.g. "openai", "gemini", "typesafe". */
    String id();

    /** Model used when the request does not set one. */
    String defaultModel();

    Capabilities capabilities();

    DecisionResult decide(DecisionRequest request);

    /** Rejects requests this provider cannot serve, before any network call. */
    default void checkSupported(DecisionRequest request) {
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
