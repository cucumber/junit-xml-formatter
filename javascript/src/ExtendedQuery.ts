import * as assert from 'node:assert'

import {
  Envelope,
  Feature,
  getWorstTestStepResult,
  GherkinDocument,
  Pickle,
  PickleStep,
  Step,
  TestCase,
  TestCaseFinished,
  TestCaseStarted,
  TestRunFinished,
  TestRunStarted,
  TestStep,
  TestStepFinished,
  TestStepResultStatus,
  TimeConversion,
} from '@cucumber/messages'
import { ArrayMultimap } from '@teppeis/multimaps'

export class ExtendedQuery {
  private testRunStarted: TestRunStarted
  private testRunFinished: TestRunFinished
  private testCaseStarted: Array<TestCaseStarted> = []
  private readonly stepById: Map<string, Step> = new Map()
  private readonly pickleById: Map<string, Pickle> = new Map()
  private readonly pickleStepById: Map<string, PickleStep> = new Map()
  private readonly testCaseById: Map<string, TestCase> = new Map()
  private readonly testStepById: Map<string, TestStep> = new Map()
  private readonly testCaseFinishedByTestCaseStartedId: Map<string, TestCaseFinished> = new Map()
  private readonly testStepFinishedByTestCaseStartedId: ArrayMultimap<string, TestStepFinished> =
    new ArrayMultimap()

  update(envelope: Envelope) {
    if (envelope.gherkinDocument) {
      this.updateGherkinDocument(envelope.gherkinDocument)
    }
    if (envelope.pickle) {
      this.updatePickle(envelope.pickle)
    }
    if (envelope.testRunStarted) {
      this.testRunStarted = envelope.testRunStarted
    }
    if (envelope.testCase) {
      this.updateTestCase(envelope.testCase)
    }
    if (envelope.testCaseStarted) {
      this.updateTestCaseStarted(envelope.testCaseStarted)
    }
    if (envelope.testStepFinished) {
      this.updateTestStepFinished(envelope.testStepFinished)
    }
    if (envelope.testCaseFinished) {
      this.updateTestCaseFinished(envelope.testCaseFinished)
    }
    if (envelope.testRunFinished) {
      this.testRunFinished = envelope.testRunFinished
    }
  }

  private updateGherkinDocument(gherkinDocument: GherkinDocument) {
    if (gherkinDocument.feature) {
      this.updateFeature(gherkinDocument.feature)
    }
  }

  private updateFeature(feature: Feature) {
    feature.children.forEach((featureChild) => {
      if (featureChild.background) {
        this.updateSteps(featureChild.background.steps)
      }
      if (featureChild.scenario) {
        this.updateSteps(featureChild.scenario.steps)
      }
      if (featureChild.rule) {
        featureChild.rule.children.forEach((ruleChild) => {
          if (ruleChild.background) {
            this.updateSteps(ruleChild.background.steps)
          }
          if (ruleChild.scenario) {
            this.updateSteps(ruleChild.scenario.steps)
          }
        })
      }
    })
  }

  private updateSteps(steps: ReadonlyArray<Step>) {
    steps.forEach((step) => this.stepById.set(step.id, step))
  }

  private updatePickle(pickle: Pickle) {
    this.pickleById.set(pickle.id, pickle)
    pickle.steps.forEach((pickleStep) => this.pickleStepById.set(pickleStep.id, pickleStep))
  }

  private updateTestCase(testCase: TestCase) {
    this.testCaseById.set(testCase.id, testCase)
    testCase.testSteps.forEach((testStep) => this.testStepById.set(testStep.id, testStep))
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

  private updateTestStepFinished(testStepFinished: TestStepFinished) {
    this.testStepFinishedByTestCaseStartedId.put(
      testStepFinished.testCaseStartedId,
      testStepFinished
    )
  }

  private updateTestCaseFinished(testCaseFinished: TestCaseFinished) {
    this.testCaseFinishedByTestCaseStartedId.set(
      testCaseFinished.testCaseStartedId,
      testCaseFinished
    )
  }

  findStepBy(pickleStep: PickleStep) {
    const [astNodeId] = pickleStep.astNodeIds
    assert.ok('Expected PickleStep to have an astNodeId')
    return this.stepById.get(astNodeId)
  }

  findPickleBy(testCaseStarted: TestCaseStarted) {
    const testCase = this.findTestCaseBy(testCaseStarted)
    assert.ok(testCase, 'Expected to find TestCase from TestCaseStarted')
    return this.pickleById.get(testCase.pickleId)
  }

  findPickleStepBy(testStep: TestStep) {
    assert.ok(testStep.pickleStepId, 'Expected TestStep to have a pickleStepId')
    return this.pickleStepById.get(testStep.pickleStepId)
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

  findMostSevereTestStepResultBy(testCaseStarted: TestCaseStarted) {
    return getWorstTestStepResult(
      this.findTestStepFinishedAndTestStepBy(testCaseStarted).map(
        ([testStepFinished]) => testStepFinished.testStepResult
      )
    )
  }

  findTestStepBy(testStepFinished: TestStepFinished) {
    return this.testStepById.get(testStepFinished.testStepId)
  }

  findTestStepFinishedAndTestStepBy(
    testCaseStarted: TestCaseStarted
  ): ReadonlyArray<[TestStepFinished, TestStep]> {
    return this.testStepFinishedByTestCaseStartedId
      .get(testCaseStarted.id)
      .map((testStepFinished) => {
        const testStep = this.findTestStepBy(testStepFinished)
        assert.ok(testStep, 'Expected to find TestStep by TestStepFinished')
        return [testStepFinished, testStep]
      })
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
      const testStepResults = this.findTestStepFinishedAndTestStepBy(testCaseStarted).map(
        ([testStepFinished]) => testStepFinished.testStepResult
      )
      const mostSevereResult = getWorstTestStepResult(testStepResults)
      result[mostSevereResult.status]++
    }
    return result
  }
}
