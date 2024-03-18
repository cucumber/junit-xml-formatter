package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.TestCase;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestStep;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

class XmlReportData {

    private final Query query = new Query();

    private static final long MILLIS_PER_SECOND = SECONDS.toMillis(1L);
    private final Map<String, String> pickleAstNodeIdToLongName = new ConcurrentHashMap<>();

    void collect(Envelope envelope) {
        query.update(envelope);
        envelope.getGherkinDocument().ifPresent(this::source);
    }

    private void source(GherkinDocument event) {
        event.getFeature().ifPresent(feature -> {
            feature.getChildren().forEach(featureChild -> {
                featureChild.getRule().ifPresent(rule -> {
                    rule.getChildren().forEach(ruleChild -> {
                        ruleChild.getScenario().ifPresent(scenario -> {
                            scenario(rule, scenario);
                        });
                    });
                });
                featureChild.getScenario().ifPresent(scenario -> {
                    scenario(null, scenario);
                });
            });
        });
    }

    private void scenario(Rule rule, Scenario scenario) {
        String rulePrefix = rule == null ? "" : rule.getName() + " - ";
        pickleAstNodeIdToLongName.put(scenario.getId(), rulePrefix + scenario.getName());

        List<Examples> examples = scenario.getExamples();
        for (int examplesIndex = 0; examplesIndex < examples.size(); examplesIndex++) {
            Examples currentExamples = examples.get(examplesIndex);
            List<TableRow> tableRows = currentExamples.getTableBody();
            for (int exampleIndex = 0; exampleIndex < tableRows.size(); exampleIndex++) {
                TableRow currentExample = tableRows.get(exampleIndex);
                StringBuilder suffix = new StringBuilder(" - ");
                if (!currentExamples.getName().isEmpty()) {
                    suffix.append(currentExamples.getName()).append(" - ");
                }
                suffix.append("Example #").append(examplesIndex + 1).append(".").append(exampleIndex + 1);
                pickleAstNodeIdToLongName.put(currentExample.getId(), rulePrefix + scenario.getName() + suffix);
            }
        }
    }

    double getSuiteDurationInSeconds() {
        return query.findTestRunDuration()
                .orElse(Duration.ZERO)
                .toMillis() / (double) MILLIS_PER_SECOND;
    }

    double getDurationInSeconds(TestCaseStarted testCaseStarted) {
        return query.findTestCaseDurationByTestCaseStarted(testCaseStarted)
                .orElse(Duration.ZERO)
                .toMillis() / (double) MILLIS_PER_SECOND;
    }

    Map<TestStepResultStatus, Long> getTestCaseStatusCounts() {
        // @formatter:off
        return query.findAllTestCaseStarted().stream()
                .map(query::findMostSevereTestStepResultStatusByTestCaseStarted)
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
        Pickle pickle = query.findPickleByTestCaseStarted(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No pickle for " + testCaseStarted.getId()));
        List<String> astNodeIds = pickle.getAstNodeIds();
        String pickleAstNodeId = astNodeIds.get(astNodeIds.size() - 1);
        return pickleAstNodeIdToLongName.getOrDefault(pickleAstNodeId, pickle.getName());
    }

    public String getFeatureName(TestCaseStarted testCaseStarted) {
        return query.findFeatureNameByTestCaseStarted(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No feature for " + testCaseStarted));
    }

    List<Map.Entry<String, String>> getStepsAndResult(TestCaseStarted testCaseStarted) {
        TestCase testCase = query.findTestCaseByTestCaseStarted(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No testcase for " + testCaseStarted.getId()));
        return testCase.getTestSteps().stream()
                // Exclude hooks
                .filter(testStep -> testStep.getPickleStepId().isPresent())
                .map(testStep -> {
                    String key = renderTestStepText(testStep);
                    String value = renderTestStepResult(testStep);
                    return new AbstractMap.SimpleEntry<>(key, value);
                })
                .collect(Collectors.toList());
    }

    private String renderTestStepResult(TestStep testStep) {
        return query.findTestStepFinishedByTestStep(testStep)
                .map(TestStepFinished::getTestStepResult)
                .map(TestStepResult::getStatus)
                .map(TestStepResultStatus::toString)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException("No test step finished for " + testStep.getId()));
    }

    private String renderTestStepText(TestStep testStep) {
        Optional<PickleStep> pickleStep = query.findPickleStepByTestStep(testStep);

        String stepKeyWord = pickleStep
                .flatMap(query::findStepByPickleStep)
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
        return query.findMostSevereTestStepResultStatusByTestCaseStarted(testCaseStarted)
                .orElse(SCENARIO_WITH_NO_STEPS);
    }

}