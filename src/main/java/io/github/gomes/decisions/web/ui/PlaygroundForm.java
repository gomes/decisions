package io.github.gomes.decisions.web.ui;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Backing bean of the playground form. */
public class PlaygroundForm {

    @NotEmpty(message = "{playground.providers.required}")
    private List<String> providers = new ArrayList<>();

    /** Optional model override per provider id. */
    private Map<String, String> models = new HashMap<>();

    private String input;

    /** Optional JSON object/array, sent as structured data. */
    private String data;

    /** Optional image URLs, one per line. */
    private String images;

    @NotEmpty(message = "{decisions.request.questions}")
    private List<@Valid QuestionForm> questions = new ArrayList<>();

    public List<String> getProviders() { return providers; }
    public void setProviders(List<String> providers) { this.providers = providers; }
    public Map<String, String> getModels() { return models; }
    public void setModels(Map<String, String> models) { this.models = models; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public List<QuestionForm> getQuestions() { return questions; }
    public void setQuestions(List<QuestionForm> questions) { this.questions = questions; }

    public PlaygroundForm question(QuestionForm q) {
        questions.add(q);
        return this;
    }
}
