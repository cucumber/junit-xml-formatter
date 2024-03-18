package io.cucumber.junitxmlformatter;

import io.cucumber.messages.Convertor;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TestCase;
import io.cucumber.messages.types.TestCaseFinished;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestRunFinished;
import io.cucumber.messages.types.TestRunStarted;
import io.cucumber.messages.types.TestStep;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.Timestamp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class Query {
    private final Comparator<TestStepResult> testStepResultComparator = nullsFirst(
            comparing(o -> o.getStatus().ordinal()));
    private TestRunStarted testRunStarted;
    private TestRunFinished testRunFinished;
    private final Deque<TestCaseStarted> testCaseStarted = new ConcurrentLinkedDeque<>();
    private final Map<String, TestCaseFinished> testCaseFinishedByTestCaseStartedId = new ConcurrentHashMap<>();
    private final Map<String, List<TestStepFinished>> testStepFinishedByTestCaseStartedId = new ConcurrentHashMap<>();
    private final Map<String, TestStepFinished> testStepFinishedByTestStepId = new ConcurrentHashMap<>();
    private final Map<String, Pickle> pickleById = new ConcurrentHashMap<>();
    private final Map<String, TestCase> testCaseById = new ConcurrentHashMap<>();
    private final Map<String, Step> stepById = new ConcurrentHashMap<>();
    private final Map<String, Scenario> scenarioById = new ConcurrentHashMap<>();
    private final Map<String, PickleStep> pickleStepById = new ConcurrentHashMap<>();
    private final Map<String, Feature> featureByScenarioId = new ConcurrentHashMap<>();

    public void update(Envelope envelope) {
        envelope.getTestRunStarted().ifPresent(event -> this.testRunStarted = event);
        envelope.getTestRunFinished().ifPresent(event -> this.testRunFinished = event);

        envelope.getTestCaseStarted().ifPresent(testCaseStarted::add);
        envelope.getTestCaseFinished().ifPresent(event -> testCaseFinishedByTestCaseStartedId.put(event.getTestCaseStartedId(), event));
        envelope.getTestStepFinished().ifPresent(event -> {
            testStepFinishedByTestStepId.put(event.getTestStepId(), event);
            testStepFinishedByTestCaseStartedId.compute(event.getTestCaseStartedId(), addToList(event));
        });
        envelope.getGherkinDocument()
                .flatMap(GherkinDocument::getFeature)
                .ifPresent(feature -> feature.getChildren()
                        .forEach(featureChild -> {
                            featureChild.getRule().ifPresent(rule -> rule.getChildren()
                                    .forEach(ruleChild -> {
                                        ruleChild.getBackground()
                                                .ifPresent(background -> background.getSteps()
                                                        .forEach(step -> stepById.put(step.getId(), step)));
                                        ruleChild.getScenario()
                                                .ifPresent(scenario -> {
                                                    scenario.getSteps().forEach(step -> stepById.put(step.getId(), step));
                                                    scenarioById.put(scenario.getId(), scenario);
                                                    featureByScenarioId.put(scenario.getId(), feature);
                                                });
                                    }));
                            featureChild.getBackground()
                                    .ifPresent(background -> background.getSteps()
                                            .forEach(step -> stepById.put(step.getId(), step)));
                            featureChild.getScenario().ifPresent(scenario -> {
                                scenario.getSteps().forEach(step -> stepById.put(step.getId(), step));
                                scenarioById.put(scenario.getId(), scenario);
                                featureByScenarioId.put(scenario.getId(), feature);
                            });
                        }));
        envelope.getPickle().ifPresent(event -> {
            pickleById.put(event.getId(), event);
            event.getSteps().forEach(pickleStep -> pickleStepById.put(pickleStep.getId(), pickleStep));
        });
        envelope.getTestCase().ifPresent(event -> testCaseById.put(event.getId(), event));
    }

    private static BiFunction<String, List<TestStepFinished>, List<TestStepFinished>> addToList(TestStepFinished event) {
        return (k, v) -> {
            if (v == null) {
                List<TestStepFinished> list = new ArrayList<>();
                list.add(event);
                return list;
            }
            v.add(event);
            return v;
        };
    }

    public Optional<TestRunStarted> findTestRunStarted() {
        return ofNullable(testRunStarted);
    }

    public Optional<TestRunFinished> findTestRunFinished() {
        return ofNullable(testRunFinished);
    }

    public Optional<Feature> findFeatureByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return findPickleByTestCaseStarted(testCaseStarted)
                .flatMap(this::findScenarioByPickle)
                .flatMap(this::findFeatureByScenario);
    }
    
    public Optional<TestCaseFinished> findTestCaseFinishedByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return ofNullable(testCaseFinishedByTestCaseStartedId.get(testCaseStarted.getId()));
    }

    public Optional<TestStepResult> findMostSevereTestStepResultStatusByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return findTestStepFinishedByTestCaseStarted(testCaseStarted)
                .stream()
                .map(TestStepFinished::getTestStepResult)
                .max(testStepResultComparator);
    }

    public List<TestStepFinished> findTestStepFinishedByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return testStepFinishedByTestCaseStartedId.getOrDefault(testCaseStarted.getId(), Collections.emptyList());
    }

    public Optional<TestStepFinished> findTestStepFinishedByTestStep(TestStep testStep) {
        requireNonNull(testStep);
        return ofNullable(testStepFinishedByTestStepId.get(testStep.getId()));
    }

    public Optional<Step> findStepByPickleStep(PickleStep pickleStep) {
        requireNonNull(pickleStep);
        String stepId = pickleStep.getAstNodeIds().get(0);
        return ofNullable(stepById.get(stepId));
    }

    public Optional<Scenario> findScenarioByPickle(Pickle pickle) {
        requireNonNull(pickle);
        String scenarioId = pickle.getAstNodeIds().get(0);
        return ofNullable(scenarioById.get(scenarioId));
    }

    public Optional<TestCase> findTestCaseByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return ofNullable(testCaseById.get(testCaseStarted.getTestCaseId()));
    }

    public Optional<Pickle> findPickleByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return findTestCaseByTestCaseStarted(testCaseStarted)
                .map(TestCase::getPickleId)
                .map(pickleById::get);
    }

    public Optional<Feature> findFeatureByScenario(Scenario scenario) {
        requireNonNull(scenario);
        return ofNullable(featureByScenarioId.get(scenario.getId()));
    }

    public Optional<Duration> findTestCaseDurationByTestCaseStarted(TestCaseStarted testCaseStarted){
        requireNonNull(testCaseStarted);
        Timestamp started = testCaseStarted.getTimestamp();
        return findTestCaseFinishedByTestCaseStarted(testCaseStarted)
                .map(TestCaseFinished::getTimestamp)
                .map(finished -> Duration.between(
                        Convertor.toInstant(started),
                        Convertor.toInstant(finished)
                ));
    }

    public Optional<Duration> findTestRunDuration(){
        if (testRunStarted == null || testRunFinished == null) {
            return Optional.empty();
        }
        Duration between = Duration.between(
                Convertor.toInstant(testRunStarted.getTimestamp()),
                Convertor.toInstant(testRunFinished.getTimestamp())
        );
        return Optional.of(between);
    }

    public List<TestCaseStarted> findAllTestCaseStarted() {
        return new ArrayList<>(testCaseStarted);
    }

    public Optional<PickleStep> findPickleStepByTestStep(TestStep testStep) {
        requireNonNull(testCaseStarted);
        return testStep.getPickleStepId()
                .map(pickleStepById::get);
    }

    public Optional<String> findFeatureNameByTestCaseStarted(TestCaseStarted testCaseStarted) {
        requireNonNull(testCaseStarted);
        return findFeatureByTestCaseStarted(testCaseStarted)
                .map(Feature::getName);
    }
}
