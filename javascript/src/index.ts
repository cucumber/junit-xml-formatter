import { Query as GherkinQuery } from '@cucumber/gherkin-utils'
import { Envelope, TestCaseStarted, TestStepResultStatus } from '@cucumber/messages'
import xmlbuilder from 'xmlbuilder'

import { ExtendedQuery } from './ExtendedQuery.js'
import { countStatuses, durationToSeconds } from './helpers.js'
import * as assert from 'node:assert'

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
          const element = builder.ele('testcase', {
            classname: testCase.classname,
            name: testCase.name,
            time: testCase.time,
          })
        }

        write(builder.end({ pretty: true }))
      }
    })
  },
}

interface Report {
  time: number
  tests: number
  skipped: number
  failures: number
  errors: number
  testCases: ReadonlyArray<TestCase>
}

interface TestCase {
  classname: string
  name: string
  time: number
}

function makeReport(query: ExtendedQuery): Report {
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

function makeTestCases(query: ExtendedQuery): ReadonlyArray<TestCase> {
  return query.findAllTestCaseStarted().map((testCaseStarted) => {
    const pickle = query.findPickleBy(testCaseStarted)
    assert.ok(pickle, 'Expected to find Pickle by TestCaseStarted')
    return {
      classname: pickle.uri,
      name: pickle.name,
      time: durationToSeconds(query.findTestCaseDurationBy(testCaseStarted)),
    }
  })
}
