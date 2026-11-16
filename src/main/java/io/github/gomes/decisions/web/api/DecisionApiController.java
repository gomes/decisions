package io.github.gomes.decisions.web.api;

import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.ProviderStatus;
import io.github.gomes.decisions.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral decision API.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/providers}: every known provider and whether it is enabled</li>
 *   <li>{@code POST /api/v1/decisions?provider=<id>[&model=<model>]}</li>
 *   <li>{@code POST /api/v1/compare[?providers=a,b][&model.<id>=<model>]}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class DecisionApiController {

    private final DecisionService service;

    public DecisionApiController(DecisionService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<ProviderStatus> providers() {
        return service.providers();
    }

    @PostMapping("/decisions")
    public DecisionResult decide(@RequestParam String provider,
                                 @RequestParam(required = false) String model,
                                 @Valid @RequestBody DecisionRequest request) {
        return service.decide(provider, model == null ? request : request.withModel(model));
    }

    @PostMapping("/compare")
    public List<DecisionService.Outcome> compare(@RequestParam(required = false) List<String> providers,
                                                 @RequestParam Map<String, String> params,
                                                 @Valid @RequestBody DecisionRequest request) {
        Map<String, String> models = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("model.") && !v.isBlank()) models.put(k.substring("model.".length()), v);
        });
        return service.compare(request, providers, models);
    }
}
