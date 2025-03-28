package io.cucumber.junitxmlformatter;

import io.cucumber.messages.Convertor;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestRunStarted;
import io.cucumber.messages.types.TestStep;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;
import io.cucumber.query.NamingStrategy;
import io.cucumber.query.Query;

import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

class XmlReportData {

    private final Query query = new Query();
    private final NamingStrategy namingStrategy;

    private static final long MILLIS_PER_SECOND = SECONDS.toMillis(1L);

    XmlReportData(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

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
        return query.countMostSevereTestStepResultStatus();
    }

    int getTestCaseCount() {
        return query.findAllTestCaseStarted().size();
    }

    private Pickle getPickle(TestCaseStarted testCaseStarted) {
        return query.findPickleBy(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No pickle for " + testCaseStarted.getId()));
    }

    String getPickleName(TestCaseStarted testCaseStarted) {
        return query.findNameOf(getPickle(testCaseStarted), namingStrategy);
    }

    String getFeatureName(TestCaseStarted testCaseStarted) {
        return query.findFeatureBy(testCaseStarted)
                .map(Feature::getName)
                .orElseGet(() -> this.getPickle(testCaseStarted).getUri());
    }

    List<Entry<String, String>> getStepsAndResult(TestCaseStarted testCaseStarted) {
        return query.findTestStepFinishedAndTestStepBy(testCaseStarted)
                .stream()
                // Exclude hooks
                .filter(entry -> entry.getValue().getPickleStepId().isPresent())
                .map(testStep -> {
                    String key = renderTestStepText(testStep.getValue());
                    String value = renderTestStepResult(testStep.getKey());
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
        return query.findMostSevereTestStepResultBy(testCaseStarted)
                .orElse(SCENARIO_WITH_NO_STEPS);
    }

    public Optional<String> getTestRunStartedAt() {
        return query.findTestRunStarted()
                .map(TestRunStarted::getTimestamp)
                .map(Convertor::toInstant)
                .map(ISO_INSTANT::format);
    }
}
