import { Envelope, Pickle, TestCaseStarted, TestStepResultStatus } from '@cucumber/messages'
import {
  NamingStrategy,
  namingStrategy,
  NamingStrategyExampleName,
  NamingStrategyFeatureName,
  NamingStrategyLength,
  Query,
} from '@cucumber/query'
import xmlbuilder from 'xmlbuilder'

import { countStatuses, durationToSeconds, ensure, formatStep, formatTimestamp } from './helpers'
import { Options } from './types'

const DEFAULT_NAMING_STRATEGY = namingStrategy(
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

export class JUnitXmlPrinter {
  private readonly query = new Query()
  private readonly builder: xmlbuilder.XMLElement
  private readonly testClassName?: string
  private readonly namingStrategy: NamingStrategy

  constructor(
    options: Options,
    private readonly write: (content: string) => void
  ) {
    this.testClassName = options.testClassName
    this.namingStrategy = options.testNamingStrategy ?? DEFAULT_NAMING_STRATEGY
    this.builder = xmlbuilder
      .create('testsuite', { invalidCharReplacement: '' })
      .att('name', options.suiteName || 'Cucumber')
  }

  update(envelope: Envelope): void {
    this.query.update(envelope)

    if (envelope.testRunFinished) {
      const testSuite = this.makeReport()
      this.builder.att('time', testSuite.time)
      this.builder.att('tests', testSuite.tests)
      this.builder.att('skipped', testSuite.skipped)
      this.builder.att('failures', testSuite.failures)
      this.builder.att('errors', testSuite.errors)

      if (testSuite.timestamp) {
        this.builder.att('timestamp', testSuite.timestamp)
      }

      for (const testCase of testSuite.testCases) {
        const testcaseElement = this.builder.ele('testcase', {
          classname: testCase.classname,
          name: testCase.name,
          time: testCase.time,
        })
        if (testCase.failure) {
          const failureElement = testcaseElement.ele(testCase.failure.kind)
          if (testCase.failure.kind === 'failure' && testCase.failure.type) {
            failureElement.att('type', testCase.failure.type)
          }
          if (testCase.failure.kind === 'failure' && testCase.failure.message) {
            failureElement.att('message', testCase.failure.message)
          }
          if (testCase.failure.stack) {
            failureElement.cdata(testCase.failure.stack)
          }
        }
        if (testCase.output) {
          testcaseElement.ele('system-out').cdata(testCase.output)
        }
      }

      this.write(this.builder.end({ pretty: true }))
    }
  }

  private makeReport(): ReportSuite {
    const statuses = this.query.countMostSevereTestStepResultStatus()
    return {
      time: durationToSeconds(this.query.findTestRunDuration()),
      tests: this.query.countTestCasesStarted(),
      skipped: countStatuses(statuses, (status) => status === TestStepResultStatus.SKIPPED),
      failures: countStatuses(
        statuses,
        (status) =>
          status !== TestStepResultStatus.PASSED && status !== TestStepResultStatus.SKIPPED
      ),
      errors: 0,
      testCases: this.makeTestCases(),
      timestamp: formatTimestamp(this.query.findTestRunStarted()),
    }
  }

  private makeTestCases(): ReadonlyArray<ReportTestCase> {
    return this.query
      .findAllTestCaseStartedOrderBy(
        (q, testCaseStarted) => q.findPickleBy(testCaseStarted),
        this.pickleComparator
      )
      .map((testCaseStarted) => {
        const pickle = ensure(
          this.query.findPickleBy(testCaseStarted),
          'Expected to find Pickle by TestCaseStarted'
        )
        const lineage = ensure(
          this.query.findLineageBy(pickle),
          'Expected to find Lineage by Pickle'
        )

        return {
          classname: this.testClassName ?? lineage.feature?.name ?? pickle.uri,
          name: this.namingStrategy.reduce(lineage, pickle),
          time: durationToSeconds(this.query.findTestCaseDurationBy(testCaseStarted)),
          failure: this.makeFailure(testCaseStarted),
          output: this.query
            .findTestStepFinishedAndTestStepBy(testCaseStarted)
            .filter(([, testStep]) => !!testStep.pickleStepId)
            .map(([testStepFinished, testStep]) => {
              const pickleStep = ensure(
                this.query.findPickleStepBy(testStep),
                'Expected to find PickleStep by TestStep'
              )
              const gherkinStep = ensure(
                this.query.findStepBy(pickleStep),
                'Expected to find Step by PickleStep'
              )
              return formatStep(gherkinStep, pickleStep, testStepFinished.testStepResult.status)
            })
            .join('\n'),
        }
      })
  }

  private pickleComparator(a: Pickle, b: Pickle): number {
    if (a.uri !== b.uri) {
      return a.uri.localeCompare(b.uri)
    }
    if (!a.location) {
      return !b.location ? 0 : -1
    } else if (!b.location) {
      return 1
    }
    return a.location.line - b.location.line
  }

  private makeFailure(testCaseStarted: TestCaseStarted): ReportFailure | undefined {
    const result = this.query.findMostSevereTestStepResultBy(testCaseStarted)
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
}
