import * as assert from 'node:assert'

import { Query as GherkinQuery } from '@cucumber/gherkin-utils'
import { Envelope, TestCaseStarted, TestStepResultStatus } from '@cucumber/messages'
import xmlbuilder from 'xmlbuilder'

import { ExtendedQuery } from './ExtendedQuery.js'
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

export default {
  type: 'formatter',
  formatter({
    on,
    write,
  }: {
    on: (type: 'message', handler: (message: Envelope) => void) => void
    write: (content: string) => void
  }) {
    const gherkinQuery = new GherkinQuery()
    const cucumberQuery = new ExtendedQuery()
    const builder = xmlbuilder
      .create('testsuite', { invalidCharReplacement: '' })
      .att('name', 'Cucumber')

    on('message', (message) => {
      gherkinQuery.update(message)
      cucumberQuery.update(message)

      if (message.testRunFinished) {
        const testSuite = makeReport(cucumberQuery)
        builder.att('time', testSuite.time)
        builder.att('tests', testSuite.tests)
        builder.att('skipped', testSuite.skipped)
        builder.att('failures', testSuite.failures)
        builder.att('errors', testSuite.errors)

        for (const testCase of testSuite.testCases) {
          const testcaseElement = builder.ele('testcase', {
            classname: testCase.classname,
            name: testCase.name,
            time: testCase.time,
          })
          if (testCase.failure) {
            testcaseElement.ele(testCase.failure.type)
          }
          testcaseElement.ele('system-out', {}).cdata(testCase.output)
        }

        write(builder.end({ pretty: true }))
      }
    })
  },
}

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
  type: 'failure' | 'skipped'
}

function makeReport(query: ExtendedQuery): ReportSuite {
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

function makeTestCases(query: ExtendedQuery): ReadonlyArray<ReportTestCase> {
  return query.findAllTestCaseStarted().map((testCaseStarted) => {
    const pickle = query.findPickleBy(testCaseStarted)
    assert.ok(pickle, 'Expected to find Pickle by TestCaseStarted')
    const lineage = query.findLineageBy(pickle)

    return {
      classname: lineage?.feature?.name ?? pickle.uri,
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

function makeFailure(
  query: ExtendedQuery,
  testCaseStarted: TestCaseStarted
): ReportFailure | undefined {
  const result = query.findMostSevereTestStepResultBy(testCaseStarted)
  if (result.status === TestStepResultStatus.PASSED) {
    return undefined
  }
  return {
    type: result.status === TestStepResultStatus.SKIPPED ? 'skipped' : 'failure',
  }
}
