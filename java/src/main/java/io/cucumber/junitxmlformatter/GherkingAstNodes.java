package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableRow;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

class GherkingAstNodes {
    private final Feature feature;
    private final Rule rule;
    private final Scenario scenario;
    private final Examples examples;
    private final TableRow example;
    private final Integer examplesIndex;
    private final Integer exampleIndex;

    public GherkingAstNodes(Feature feature, Rule rule, Scenario scenario) {
        this(feature, rule, scenario, null, null, null, null);
    }

    public GherkingAstNodes(Feature feature, Rule rule, Scenario scenario, Integer examplesIndex, Examples examples, Integer exampleIndex, TableRow example) {
        this.feature = requireNonNull(feature);
        this.rule = rule;
        this.scenario = requireNonNull(scenario);
        this.examplesIndex = examplesIndex;
        this.examples = examples;
        this.exampleIndex = exampleIndex;
        this.example = example;
    }

    public Feature feature() {
        return feature;
    }

    public Optional<Rule> rule() {
        return Optional.ofNullable(rule);
    }

    public Scenario scenario() {
        return scenario;
    }

    public Optional<Examples> examples() {
        return Optional.ofNullable(examples);
    }

    public Optional<TableRow> example() {
        return Optional.ofNullable(example);
    }

    public Optional<Integer> examplesIndex() {
        return Optional.ofNullable(examplesIndex);
    }

    public Optional<Integer> exampleIndex() {
        return Optional.ofNullable(exampleIndex);
    }
}
