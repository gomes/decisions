package io.github.gomes.decisions.spi;

/**
 * What a provider accepts.
 *
 * @param maxQuestions  max questions per request
 * @param images        whether image input is accepted
 * @param structuredData whether JSON data is sent natively (otherwise it is serialized into the text)
 */
public record Capabilities(int maxQuestions, boolean images, boolean structuredData) {
}
