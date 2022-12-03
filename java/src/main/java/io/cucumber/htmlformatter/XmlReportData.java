package io.cucumber.htmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.TestCase;
import io.cucumber.messages.types.TestCaseFinished;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestRunFinished;
import io.cucumber.messages.types.TestRunStarted;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static io.cucumber.messages.TimeConversion.timestampToJavaInstant;
import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

class XmlReportData {

    private static final long MILLIS_PER_SECOND = SECONDS.toMillis(1L);
    private final Comparator<TestStepResult> testStepResultComparator = nullsFirst(
            comparing(o -> o.getStatus().ordinal()));
    private Instant testRunStarted;
    private Instant testRunFinished;
    private final Deque<String> testCaseStartedIds = new ConcurrentLinkedDeque<>();
    private final Map<String, Instant> testCaseStartedIdToStartedInstant = new ConcurrentHashMap<>();
    private final Map<String, Instant> testCaseStartedIdToFinishedInstant = new ConcurrentHashMap<>();
    private final Map<String, TestStepResult> testCaseStartedIdToResult = new ConcurrentHashMap<>();
    private final Map<String, String> testCaseStartedIdToTestCaseId = new ConcurrentHashMap<>();
    private final Map<String, String> testCaseIdToPickleId = new ConcurrentHashMap<>();
    private final Map<String, String> pickleIdToPickleName = new ConcurrentHashMap<>();
    private final Map<String, String> pickleIdToScenarioAstNodeId = new ConcurrentHashMap<>();
    private final Map<String, String> scenarioAstNodeIdToFeatureName = new ConcurrentHashMap<>();

    void collect(Envelope envelope) {
        envelope.getTestRunStarted().ifPresent(this::testRunStarted);
        envelope.getTestRunFinished().ifPresent(this::testRunFinished);
        envelope.getTestCaseStarted().ifPresent(this::testCaseStarted);
        envelope.getTestCaseFinished().ifPresent(this::testCaseFinished);
        envelope.getTestStepFinished().ifPresent(this::testStepFinished);
        envelope.getGherkinDocument().ifPresent(this::source);
        envelope.getPickle().ifPresent(this::pickle);
        envelope.getTestCase().ifPresent(this::testCase);
    }

    void testRunStarted(TestRunStarted event) {
        this.testRunStarted = timestampToJavaInstant(event.getTimestamp());
    }

    void testRunFinished(TestRunFinished event) {
        this.testRunFinished = timestampToJavaInstant(event.getTimestamp());
    }

    void testCaseStarted(TestCaseStarted event) {
        this.testCaseStartedIds.add(event.getId());
        this.testCaseStartedIdToStartedInstant.put(event.getId(), timestampToJavaInstant(event.getTimestamp()));
        this.testCaseStartedIdToTestCaseId.put(event.getId(), event.getTestCaseId());
    }

    void testCaseFinished(TestCaseFinished event) {
        this.testCaseStartedIdToFinishedInstant.put(event.getTestCaseStartedId(),
                timestampToJavaInstant(event.getTimestamp()));
    }

    void testStepFinished(TestStepFinished event) {
        testCaseStartedIdToResult.compute(event.getTestCaseStartedId(),
                (__, previousStatus) -> mostSevereResult(previousStatus, event.getTestStepResult()));
    }

    void source(GherkinDocument event) {
        event.getFeature().ifPresent(feature -> {
            feature.getChildren().forEach(featureChild -> {
                featureChild.getRule().ifPresent(rule -> {
                    rule.getChildren().forEach(ruleChild -> {
                        ruleChild.getScenario().ifPresent(scenario -> {
                            scenarioAstNodeIdToFeatureName.put(scenario.getId(), feature.getName());
                        });
                    });
                });
                featureChild.getScenario().ifPresent(scenario -> {
                    scenarioAstNodeIdToFeatureName.put(scenario.getId(), feature.getName());
                });
            });
        });
    }

    void pickle(Pickle pickle) {
        pickleIdToPickleName.put(pickle.getId(), pickle.getName());
        // @formatter:off
        pickle.getAstNodeIds().stream()
                .findFirst()
                .ifPresent(id -> pickleIdToScenarioAstNodeId.put(pickle.getId(), id));
        // @formatter:on
    }

    void testCase(TestCase testCase) {
        testCaseIdToPickleId.put(testCase.getId(), testCase.getPickleId());
    }

    private TestStepResult mostSevereResult(TestStepResult a, TestStepResult b) {
        return testStepResultComparator.compare(a, b) >= 0 ? a : b;
    }

    double getSuiteDurationInSeconds() {
        if (testRunStarted == null || testRunFinished == null) {
            return 0;
        }
        return durationInSeconds(testRunStarted, testRunFinished);
    }

    double getDurationInSeconds(String testCaseStartedId) {
        return durationInSeconds(testCaseStartedIdToStartedInstant.get(testCaseStartedId),
                testCaseStartedIdToFinishedInstant.get(testCaseStartedId));
    }

    private static double durationInSeconds(Instant testRunStarted, Instant testRunFinished) {
        return Duration.between(testRunStarted, testRunFinished).toMillis() / (double) MILLIS_PER_SECOND;
    }

    Map<TestStepResultStatus, Long> getTestCaseStatusCounts() {
        // @formatter:off
        return testCaseStartedIdToResult.values().stream()
                .map(TestStepResult::getStatus)
                .collect(groupingBy(identity(), counting()));
        // @formatter:on
    }

    int getTestCaseCount() {
        return testCaseStartedIdToStartedInstant.size();
    }

    String getPickleName(String testCaseStartedId) {
        String testCaseId = testCaseStartedIdToTestCaseId.get(testCaseStartedId);
        String pickleId = testCaseIdToPickleId.get(testCaseId);
        return pickleIdToPickleName.get(pickleId);
    }

    public String getFeatureName(String testCaseStartedId) {
        String testCaseId = testCaseStartedIdToTestCaseId.get(testCaseStartedId);
        String pickleId = testCaseIdToPickleId.get(testCaseId);
        String astNodeId = pickleIdToScenarioAstNodeId.get(pickleId);
        return scenarioAstNodeIdToFeatureName.get(astNodeId);
    }

    public Deque<String> testCaseStartedIds() {
        return testCaseStartedIds;
    }

    private static final io.cucumber.messages.types.Duration ZERO_DURATION = new io.cucumber.messages.types.Duration(
            0L, 0L);
    private static final TestStepResult SCENARIO_WITH_NO_STEPS = new TestStepResult(ZERO_DURATION, null, PASSED); // By

    // definition,
    // but
    // see
    // https://github.com/cucumber/gherkin/issues/11
    public TestStepResult getTestCaseStatus(String testCaseStartedId) {
        return testCaseStartedIdToResult.getOrDefault(testCaseStartedId, SCENARIO_WITH_NO_STEPS);
    }

}