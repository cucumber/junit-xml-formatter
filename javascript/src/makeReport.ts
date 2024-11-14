import assert from 'node:assert'

import { TestCaseStarted, TestStepResultStatus } from '@cucumber/messages'
import { Query } from '@cucumber/query'

import { countStatuses, durationToSeconds, formatStep } from './helpers.js'
import {
  namingStrategy,
  NamingStrategyExampleName,
  NamingStrategyFeatureName,
  NamingStrategyLength,
} from './Lineage.js'

const NAMING_STRATEGY = namingStrategy(
  NamingStrategyLength.LONG,
  NamingStrategyFeatureName.EXCLUDE,
  NamingStrategyExampleName.NUMBER_AND_PICKLE_IF_PARAMETERIZED
)

interface ReportSuite {
  time: number
  tests: number
  skipped: number
  failures: number
  errors: number
  testCases: ReadonlyArray<ReportTestCase>
}

interface ReportTestCase {
  classname: string
  name: string
  time: number
  failure?: ReportFailure
  output: string
}

interface ReportFailure {
  kind: 'failure' | 'skipped'
  type?: string
  message?: string
  stack?: string
}

export function makeReport(query: Query): ReportSuite {
  const statuses = query.countMostSevereTestStepResultStatus()
  return {
    time: durationToSeconds(query.findTestRunDuration()),
    tests: countStatuses(statuses),
    skipped: countStatuses(statuses, (status) => status === TestStepResultStatus.SKIPPED),
    failures: countStatuses(
      statuses,
      (status) => status !== TestStepResultStatus.PASSED && status !== TestStepResultStatus.SKIPPED
    ),
    errors: 0,
    testCases: makeTestCases(query),
  }
}

function makeTestCases(query: Query): ReadonlyArray<ReportTestCase> {
  return query.findAllTestCaseStarted().map((testCaseStarted) => {
    const pickle = query.findPickleBy(testCaseStarted)
    assert.ok(pickle, 'Expected to find Pickle by TestCaseStarted')
    const feature = query.findFeatureBy(testCaseStarted)

    return {
      classname: feature?.name ?? pickle.uri,
      name: query.findNameOf(pickle, NAMING_STRATEGY),
      time: durationToSeconds(query.findTestCaseDurationBy(testCaseStarted)),
      failure: makeFailure(query, testCaseStarted),
      output: query
        .findTestStepFinishedAndTestStepBy(testCaseStarted)
        // filter out hooks
        .filter(([, testStep]) => !!testStep.pickleStepId)
        .map(([testStepFinished, testStep]) => {
          const pickleStep = query.findPickleStepBy(testStep)
          assert.ok(pickleStep, 'Expected to find PickleStep by TestStep')
          const gherkinStep = query.findStepBy(pickleStep)
          assert.ok(gherkinStep, 'Expected to find Step by PickleStep')
          return formatStep(gherkinStep, pickleStep, testStepFinished.testStepResult.status)
        })
        .join('\n'),
    }
  })
}

function makeFailure(query: Query, testCaseStarted: TestCaseStarted): ReportFailure | undefined {
  const result = query.findMostSevereTestStepResultBy(testCaseStarted)
  if (!result || result.status === TestStepResultStatus.PASSED) {
    return undefined
  }
  return {
    kind: result.status === TestStepResultStatus.SKIPPED ? 'skipped' : 'failure',
    type: result.exception?.type,
    message: result.exception?.message,
    stack: result.exception?.stackTrace ?? result.message,
  }
}
