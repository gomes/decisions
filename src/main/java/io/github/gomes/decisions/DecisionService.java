package io.github.gomes.decisions;

import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.spi.DecisionException;
import io.github.gomes.decisions.spi.DecisionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Entry point: run a request against one provider, or fan it out to several and compare. */
public final class DecisionService {

    private final ProviderRegistry registry;

    public DecisionService(ProviderRegistry registry) {
        this.registry = registry;
    }

    public ProviderRegistry registry() {
        return registry;
    }

    public DecisionResult decide(String providerId, DecisionRequest request) {
        return registry.get(providerId).decide(request);
    }

    /** Outcome of one provider in a comparison: either a result or an error message. */
    public record Outcome(String provider, DecisionResult result, String error, Integer httpStatus) {
        public boolean ok() { return result != null; }
    }

    /**
     * Sends the same request to every given provider in parallel (virtual threads). A per-provider model
     * override can be given; the request's own {@code model} is ignored because model ids differ per provider.
     */
    public List<Outcome> compare(DecisionRequest request, Collection<String> providerIds, Map<String, String> models) {
        List<DecisionProvider> targets = providerIds == null || providerIds.isEmpty()
                ? List.copyOf(registry.all())
                : providerIds.stream().map(registry::get).toList();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (DecisionProvider p : targets) {
                DecisionRequest r = request.withModel(models == null ? null : models.get(p.id()));
                futures.add(pool.submit(() -> run(p, r)));
            }
            List<Outcome> out = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                out.add(f.get());
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static Outcome run(DecisionProvider p, DecisionRequest r) {
        try {
            return new Outcome(p.id(), p.decide(r), null, null);
        } catch (DecisionException e) {
            return new Outcome(p.id(), null, e.getMessage(), e.httpStatus() > 0 ? e.httpStatus() : null);
        } catch (RuntimeException e) {
            return new Outcome(p.id(), null, "[" + p.id() + "] " + e, null);
        }
    }
}
