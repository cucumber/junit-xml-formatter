import { Query as GherkinQuery } from '@cucumber/gherkin-utils'
import { Envelope, TestStepResultStatus } from '@cucumber/messages'
import xmlbuilder from 'xmlbuilder'

import { ExtendedQuery } from './ExtendedQuery.js'
import { countStatuses, durationToSeconds } from './helpers.js'

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
        builder.att('time', durationToSeconds(cucumberQuery.findTestRunDuration()))
        const statusCounts = cucumberQuery.countMostSevereTestStepResultStatus()
        builder.att('tests', countStatuses(statusCounts))
        builder.att(
          'skipped',
          countStatuses(statusCounts, (status) => status === TestStepResultStatus.SKIPPED)
        )
        builder.att(
          'failures',
          countStatuses(
            statusCounts,
            (status) =>
              status !== TestStepResultStatus.PASSED && status !== TestStepResultStatus.SKIPPED
          )
        )
        builder.att('errors', 0)
        write(builder.end({ pretty: true }))
      }
    })
  },
}
