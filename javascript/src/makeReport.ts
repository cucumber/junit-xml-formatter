import { TestCaseStarted, TestStepResultStatus } from '@cucumber/messages'
import {
  namingStrategy,
  NamingStrategyExampleName,
  NamingStrategyFeatureName,
  NamingStrategyLength,
  Query,
} from '@cucumber/query'

import { countStatuses, durationToSeconds, ensure, formatStep, formatTimestamp } from './helpers.js'

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
  timestamp?: string
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

export function makeReport(query, customNamingStrategy = NAMING_STRATEGY): ReportSuite {
  const statuses = query.countMostSevereTestStepResultStatus()
  return {
    time: durationToSeconds(query.findTestRunDuration()),
    tests: query.countTestCasesStarted(),
    skipped: countStatuses(statuses, (status) => status === TestStepResultStatus.SKIPPED),
    failures: countStatuses(
      statuses,
      (status) => status !== TestStepResultStatus.PASSED && status !== TestStepResultStatus.SKIPPED
    ),
    errors: 0,
    testCases: makeTestCases(query, customNamingStrategy),
    timestamp: formatTimestamp(query.findTestRunStarted()),
  }
}

function makeTestCases(query: Query, namingStrategy: NamingStrategy): ReadonlyArray<ReportTestCase> {
  return query.findAllTestCaseStarted().map((testCaseStarted) => {
    const pickle = ensure(
      query.findPickleBy(testCaseStarted),
      'Expected to find Pickle by TestCaseStarted'
    )
    const lineage = ensure(query.findLineageBy(pickle), 'Expected to find Lineage by Pickle')

    return {
      classname: lineage.feature?.name ?? pickle.uri,
      name: NAMING_STRATEGY.reduce(lineage, pickle),
      time: durationToSeconds(query.findTestCaseDurationBy(testCaseStarted)),
      failure: makeFailure(query, testCaseStarted),
      output: query
        .findTestStepFinishedAndTestStepBy(testCaseStarted)
        // filter out hooks
        .filter(([, testStep]) => !!testStep.pickleStepId)
        .map(([testStepFinished, testStep]) => {
          const pickleStep = ensure(
            query.findPickleStepBy(testStep),
            'Expected to find PickleStep by TestStep'
          )
          const gherkinStep = ensure(
            query.findStepBy(pickleStep),
            'Expected to find Step by PickleStep'
          )
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
