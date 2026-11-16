package io.github.gomes.decisions.web.ui;

import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.provider.ProviderStatus;
import io.github.gomes.decisions.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * HTML playground at {@code /}: pick enabled providers, fill the input and the questions (with
 * explanations and ready-made examples), run, and compare the answers side by side.
 */
@Controller
@ConditionalOnBooleanProperty(name = "decisions.playground.enabled", matchIfMissing = true)
public class PlaygroundController {

    private final DecisionService service;
    private final PlaygroundFormMapper mapper;
    private final ObjectMapper objectMapper;

    public PlaygroundController(DecisionService service, PlaygroundFormMapper mapper, ObjectMapper objectMapper) {
        this.service = service;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @ModelAttribute("providers")
    List<ProviderStatus> providers() {
        return service.providers();
    }

    @ModelAttribute("examples")
    List<PlaygroundExamples.Example> examples() {
        return PlaygroundExamples.all();
    }

    @GetMapping("/")
    public String show(@RequestParam(required = false) String example, Model model) {
        Optional<PlaygroundExamples.Example> chosen = PlaygroundExamples.find(example);
        PlaygroundExamples.Example ex = chosen.orElse(PlaygroundExamples.all().getFirst());
        PlaygroundForm form = ex.form().get();
        form.setProviders(defaultSelection(ex.needsImages()));
        model.addAttribute("form", form);
        model.addAttribute("selectedExample", ex.id());
        return "playground";
    }

    @PostMapping("/")
    public String run(@Valid @ModelAttribute("form") PlaygroundForm form, BindingResult errors, Model model) {
        List<String> unknown = form.getProviders().stream()
                .filter(id -> !service.enabledProviderIds().contains(id)).toList();
        if (!unknown.isEmpty()) {
            errors.rejectValue("providers", "disabled", "Provedor não habilitado: " + String.join(", ", unknown));
        }
        Optional<DecisionRequest> request = mapper.toRequest(form, errors);
        if (request.isEmpty() || errors.hasErrors()) {
            return "playground";
        }

        Map<String, String> models = form.getModels().entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().trim()));
        List<DecisionService.Outcome> outcomes = service.compare(request.get(), form.getProviders(), models);

        model.addAttribute("request", request.get());
        model.addAttribute("outcomes", outcomes);
        model.addAttribute("requestJson", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(request.get()));
        model.addAttribute("apiBaseUrl", ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
        return "playground";
    }

    /** Every enabled provider (or only the image-capable ones), local last and only if alone. */
    private List<String> defaultSelection(boolean needsImages) {
        List<String> real = service.providers().stream()
                .filter(ProviderStatus::enabled)
                .filter(p -> !needsImages || p.capabilities().images())
                .map(ProviderStatus::id)
                .filter(id -> !id.equals("local"))
                .toList();
        return real.isEmpty() ? List.of("local") : real;
    }
}
