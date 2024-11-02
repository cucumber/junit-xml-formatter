/* eslint-disable @typescript-eslint/no-non-null-assertion */
import {
  Envelope,
  getWorstTestStepResult,
  TestCase,
  TestCaseFinished,
  TestCaseStarted,
  TestRunFinished,
  TestRunStarted,
  TestStepResultStatus,
  TimeConversion,
} from '@cucumber/messages'
import { Query as CucumberQuery } from '@cucumber/query'

export class ExtendedQuery extends CucumberQuery {
  private testRunStarted: TestRunStarted
  private testRunFinished: TestRunFinished
  private readonly testCaseById: Map<string, TestCase> = new Map()
  private readonly testCaseStartedById: Map<string, TestCaseStarted> = new Map()
  private readonly testCaseFinishedByTestCaseStartedId: Map<string, TestCaseFinished> = new Map()
  private readonly finalAttemptByTestCaseId: Map<
    string,
    [TestCase, TestCaseStarted, TestCaseFinished]
  > = new Map()

  update(envelope: Envelope) {
    super.update(envelope)

    if (envelope.testRunStarted) {
      this.testRunStarted = envelope.testRunStarted
    }
    if (envelope.testCase) {
      this.testCaseById.set(envelope.testCase.id, envelope.testCase)
    }
    if (envelope.testCaseStarted) {
      this.testCaseStartedById.set(envelope.testCaseStarted.id, envelope.testCaseStarted)
    }
    if (envelope.testCaseFinished) {
      this.testCaseFinishedByTestCaseStartedId.set(
        envelope.testCaseFinished.testCaseStartedId,
        envelope.testCaseFinished
      )
      if (!envelope.testCaseFinished.willBeRetried) {
        const testCaseStarted = this.testCaseStartedById.get(
          envelope.testCaseFinished.testCaseStartedId
        )!
        const testCase = this.testCaseById.get(testCaseStarted.testCaseId)!
        this.finalAttemptByTestCaseId.set(testCase.id, [
          testCase,
          testCaseStarted,
          envelope.testCaseFinished,
        ])
      }
    }
    if (envelope.testRunFinished) {
      this.testRunFinished = envelope.testRunFinished
    }
  }

  findTestRunDuration() {
    if (!this.testRunStarted || !this.testRunFinished) {
      return undefined
    }
    return TimeConversion.millisecondsToDuration(
      TimeConversion.timestampToMillisecondsSinceEpoch(this.testRunFinished.timestamp) -
        TimeConversion.timestampToMillisecondsSinceEpoch(this.testRunStarted.timestamp)
    )
  }

  countMostSevereTestStepResultStatus(): Record<TestStepResultStatus, number> {
    const result: Record<TestStepResultStatus, number> = {
      [TestStepResultStatus.AMBIGUOUS]: 0,
      [TestStepResultStatus.FAILED]: 0,
      [TestStepResultStatus.PASSED]: 0,
      [TestStepResultStatus.PENDING]: 0,
      [TestStepResultStatus.SKIPPED]: 0,
      [TestStepResultStatus.UNDEFINED]: 0,
      [TestStepResultStatus.UNKNOWN]: 0,
    }
    for (const [testCase] of this.finalAttemptByTestCaseId.values()) {
      const statusesFromSteps = testCase.testSteps.map((testStep) => {
        return getWorstTestStepResult(this.getTestStepResults(testStep.id))
      })
      const overallStatus = getWorstTestStepResult(statusesFromSteps)
      result[overallStatus.status]++
    }
    return result
  }
}
