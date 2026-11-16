package io.github.gomes.decisions.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.DecisionException;
import io.github.gomes.decisions.provider.DecisionProvider;
import io.github.gomes.decisions.provider.ProviderRegistry;
import io.github.gomes.decisions.provider.ProviderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Runs a request against one provider, or fans it out to several and collects every outcome. */
@Service
@Validated
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final ProviderRegistry registry;

    public DecisionService(ProviderRegistry registry) {
        this.registry = registry;
    }

    public List<ProviderStatus> providers() {
        return registry.statuses();
    }

    public List<String> enabledProviderIds() {
        return registry.enabledIds();
    }

    /**
     * @throws java.util.NoSuchElementException if the provider is not enabled
     * @throws DecisionException                if the provider rejects or fails the request
     */
    public DecisionResult decide(@NotBlank String providerId, @NotNull @Valid DecisionRequest request) {
        DecisionProvider provider = registry.get(providerId);
        DecisionResult result = provider.decide(request);
        log.debug("{} answered {} questions in {} ms", providerId, result.answers().size(), result.latencyMs());
        return result;
    }

    /** Outcome of one provider in a comparison: either a result or an error message. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Outcome(String provider, DecisionResult result, String error, Integer upstreamStatus) {
        public boolean ok() {
            return result != null;
        }
    }

    /**
     * Sends the same request to the given providers (all enabled ones when empty) in parallel, on virtual
     * threads. Per-provider model overrides may be given; the request's own {@code model} is ignored here
     * because model ids differ between providers.
     */
    public List<Outcome> compare(@NotNull @Valid DecisionRequest request, Collection<String> providerIds,
                                 Map<String, String> models) {
        List<DecisionProvider> targets = providerIds == null || providerIds.isEmpty()
                ? List.copyOf(registry.enabled())
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
            throw new IllegalStateException("interrupted while comparing providers", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static Outcome run(DecisionProvider p, DecisionRequest r) {
        try {
            return new Outcome(p.id(), p.decide(r), null, null);
        } catch (DecisionException e) {
            log.warn("{} failed: {}", p.id(), e.getMessage());
            return new Outcome(p.id(), null, e.getMessage(), e.isUpstreamError() ? e.upstreamStatus() : null);
        } catch (RuntimeException e) {
            log.error("{} failed unexpectedly", p.id(), e);
            return new Outcome(p.id(), null, "[" + p.id() + "] " + e, null);
        }
    }
}
