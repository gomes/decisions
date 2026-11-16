package io.github.gomes.decisions.web.ui;

import io.github.gomes.decisions.provider.ProviderStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Small formatting helpers for the playground template ({@code ${@view.pct(p)}}). */
@Component("view")
public class ViewHelper {

    /** Probability as a whole percentage, for CSS widths (always '.'-free). */
    public long pct(Double p) {
        return p == null ? 0 : Math.round(Math.max(0, Math.min(1, p)) * 100);
    }

    /** Probability/score with up to 2 decimals, locale-independent. */
    public String num(Double v) {
        return v == null ? "—" : String.format(Locale.ROOT, "%.2f", v);
    }

    public String providerName(List<ProviderStatus> providers, String id) {
        return providers.stream().filter(p -> p.id().equals(id)).map(ProviderStatus::name).findFirst().orElse(id);
    }

    public String typeLabel(Object type) {
        return switch (String.valueOf(type).toLowerCase(Locale.ROOT)) {
            case "yes_no" -> "Sim/Não";
            case "choice" -> "Escolha";
            case "score" -> "Escala";
            default -> String.valueOf(type);
        };
    }

    /** CSS class for a confidence level. */
    public String confidenceClass(Double c) {
        if (c == null) return "conf-unknown";
        if (c >= 0.6) return "conf-high";
        if (c >= 0.25) return "conf-mid";
        return "conf-low";
    }
}
