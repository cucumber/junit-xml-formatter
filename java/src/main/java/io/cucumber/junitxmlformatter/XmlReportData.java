package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestStep;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

class XmlReportData {

    private final Query query = new Query();

    private static final long MILLIS_PER_SECOND = SECONDS.toMillis(1L);

    void collect(Envelope envelope) {
        query.update(envelope);
    }

    double getSuiteDurationInSeconds() {
        return query.findTestRunDuration()
                .orElse(Duration.ZERO)
                .toMillis() / (double) MILLIS_PER_SECOND;
    }

    double getDurationInSeconds(TestCaseStarted testCaseStarted) {
        return query.findTestCaseDurationBy(testCaseStarted)
                .orElse(Duration.ZERO)
                .toMillis() / (double) MILLIS_PER_SECOND;
    }

    Map<TestStepResultStatus, Long> getTestCaseStatusCounts() {
        // @formatter:off
        return query.findAllTestCaseStarted().stream()
                .map(query::findMostSevereTestStepResultStatusBy)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(TestStepResult::getStatus)
                .collect(groupingBy(identity(), counting()));
        // @formatter:on
    }

    int getTestCaseCount() {
        return query.findAllTestCaseStarted().size();
    }

    String getPickleName(TestCaseStarted testCaseStarted) {
        Pickle pickle = query.findPickleBy(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No pickle for " + testCaseStarted.getId()));

        return query.findAncestorsBy(pickle)
                .map(XmlReportData::getPickleName)
                .orElse(pickle.getName());
    }

    private static String getPickleName(Ancestors ancestors) {
        List<String> pieces = new ArrayList<>();

        ancestors.rule().map(Rule::getName).ifPresent(pieces::add);

        pieces.add(ancestors.scenario().getName());

        ancestors.examples().map(Examples::getName).ifPresent(pieces::add);

        String examplesPrefix = ancestors.examplesIndex()
                .map(examplesIndex -> examplesIndex + 1)
                .map(examplesIndex -> +examplesIndex + ".")
                .orElse("");

        ancestors.exampleIndex()
                .map(exampleIndex -> exampleIndex + 1)
                .map(exampleSuffix -> "Example #" + examplesPrefix + exampleSuffix)
                .ifPresent(pieces::add);

        return pieces.stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" - "));
    }

    public String getFeatureName(TestCaseStarted testCaseStarted) {
        return query.findAncestorsBy(testCaseStarted)
                .map(Ancestors::feature)
                .map(Feature::getName)
                .orElseThrow(() -> new IllegalStateException("No feature for " + testCaseStarted));
    }

    List<Map.Entry<String, String>> getStepsAndResult(TestCaseStarted testCaseStarted) {
        return query.findTestStepAndTestStepFinishedBy(testCaseStarted)
                .stream()
                // Exclude hooks
                .filter(entry -> entry.getKey().getPickleStepId().isPresent())
                .map(testStep -> {
                    String key = renderTestStepText(testStep.getKey());
                    String value = renderTestStepResult(testStep.getValue());
                    return new SimpleEntry<>(key, value);
                })
                .collect(toList());
    }

    private String renderTestStepResult(TestStepFinished testStepFinished) {
        return testStepFinished
                .getTestStepResult()
                .getStatus()
                .toString()
                .toLowerCase(Locale.ROOT);
    }

    private String renderTestStepText(TestStep testStep) {
        Optional<PickleStep> pickleStep = query.findPickleStepBy(testStep);

        String stepKeyWord = pickleStep
                .flatMap(query::findStepBy)
                .map(Step::getKeyword)
                .orElse("");

        String stepText = pickleStep
                .map(PickleStep::getText)
                .orElse("");

        return stepKeyWord + stepText;
    }

    List<TestCaseStarted> getAllTestCaseStarted() {
        return query.findAllTestCaseStarted();
    }

    private static final io.cucumber.messages.types.Duration ZERO_DURATION =
            new io.cucumber.messages.types.Duration(0L, 0L);
    // By definition, but see https://github.com/cucumber/gherkin/issues/11
    private static final TestStepResult SCENARIO_WITH_NO_STEPS = new TestStepResult(ZERO_DURATION, null, PASSED, null);

    TestStepResult getTestCaseStatus(TestCaseStarted testCaseStarted) {
        return query.findMostSevereTestStepResultStatusBy(testCaseStarted)
                .orElse(SCENARIO_WITH_NO_STEPS);
    }

}