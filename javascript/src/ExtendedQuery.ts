import * as assert from 'node:assert'

import {
  Envelope,
  getWorstTestStepResult,
  Pickle,
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
  private testCaseStarted: Array<TestCaseStarted> = []
  private readonly pickleById: Map<string, Pickle> = new Map()
  private readonly testCaseById: Map<string, TestCase> = new Map()
  private readonly testCaseFinishedByTestCaseStartedId: Map<string, TestCaseFinished> = new Map()

  update(envelope: Envelope) {
    super.update(envelope)

    if (envelope.pickle) {
      this.pickleById.set(envelope.pickle.id, envelope.pickle)
    }
    if (envelope.testRunStarted) {
      this.testRunStarted = envelope.testRunStarted
    }
    if (envelope.testCase) {
      this.testCaseById.set(envelope.testCase.id, envelope.testCase)
    }
    if (envelope.testCaseStarted) {
      this.updateTestCaseStarted(envelope.testCaseStarted)
    }
    if (envelope.testCaseFinished) {
      this.updateTestCaseFinished(envelope.testCaseFinished)
    }
    if (envelope.testRunFinished) {
      this.testRunFinished = envelope.testRunFinished
    }
  }

  private updateTestCaseStarted(testCaseStarted: TestCaseStarted) {
    // ensure this replaces any previous attempt for the same test case
    this.testCaseStarted = [
      ...this.testCaseStarted.filter(
        (existing) => existing.testCaseId !== testCaseStarted.testCaseId
      ),
      testCaseStarted,
    ]
  }

  private updateTestCaseFinished(testCaseFinished: TestCaseFinished) {
    this.testCaseFinishedByTestCaseStartedId.set(
      testCaseFinished.testCaseStartedId,
      testCaseFinished
    )
  }

  findPickleBy(testCaseStarted: TestCaseStarted) {
    const testCase = this.findTestCaseBy(testCaseStarted)
    if (!testCase) {
      return undefined
    }
    return this.pickleById.get(testCase.pickleId)
  }

  findAllTestCaseStarted(): ReadonlyArray<TestCaseStarted> {
    return [...this.testCaseStarted]
  }

  findTestCaseBy(testCaseStarted: TestCaseStarted) {
    return this.testCaseById.get(testCaseStarted.testCaseId)
  }

  findTestCaseFinishedBy(testCaseStarted: TestCaseStarted) {
    return this.testCaseFinishedByTestCaseStartedId.get(testCaseStarted.id)
  }

  findTestCaseDurationBy(testCaseStarted: TestCaseStarted) {
    const testCaseFinished = this.findTestCaseFinishedBy(testCaseStarted)
    if (!testCaseFinished) {
      return undefined
    }
    return TimeConversion.millisecondsToDuration(
      TimeConversion.timestampToMillisecondsSinceEpoch(testCaseFinished.timestamp) -
        TimeConversion.timestampToMillisecondsSinceEpoch(testCaseStarted.timestamp)
    )
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
    for (const testCaseStarted of this.testCaseStarted) {
      const testCase = this.findTestCaseBy(testCaseStarted)
      assert.ok(testCase, 'Expected to find TestCase for TestCaseStarted')
      const statusesFromSteps = testCase.testSteps.map((testStep) => {
        return getWorstTestStepResult(this.getTestStepResults(testStep.id))
      })
      const overallStatus = getWorstTestStepResult(statusesFromSteps)
      result[overallStatus.status]++
    }
    return result
  }
}
