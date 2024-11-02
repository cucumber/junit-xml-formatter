import { PickleStep, Step, TestStepResultStatus } from '@cucumber/messages'
import { expect, use } from 'chai'
import chaiAlmost from 'chai-almost'

import { countStatuses, durationToSeconds, formatStep } from './helpers.js'

use(chaiAlmost())

describe('helpers', () => {
  describe('durationToSeconds', () => {
    it('returns zero when no duration present', () => {
      expect(durationToSeconds(undefined)).to.eq(0)
    })

    it('returns duration in seconds', () => {
      expect(
        durationToSeconds({
          seconds: 3,
          nanos: 987654,
        })
      ).to.almost.eq(3.000987654)
    })
  })

  describe('countStatuses', () => {
    const statuses: Record<TestStepResultStatus, number> = {
      [TestStepResultStatus.AMBIGUOUS]: 1,
      [TestStepResultStatus.FAILED]: 2,
      [TestStepResultStatus.PASSED]: 3,
      [TestStepResultStatus.PENDING]: 4,
      [TestStepResultStatus.SKIPPED]: 5,
      [TestStepResultStatus.UNDEFINED]: 6,
      [TestStepResultStatus.UNKNOWN]: 7,
    }

    it('counts for all statuses when no predicate supplied', () => {
      expect(countStatuses(statuses)).to.eq(28)
    })

    it('honours a supplied predicate', () => {
      expect(countStatuses(statuses, (status) => status === TestStepResultStatus.UNDEFINED)).to.eq(
        6
      )
    })
  })

  describe('formatStep', () => {
    it('formats a step', () => {
      expect(
        formatStep(
          { keyword: 'Given ' } as Step,
          { text: 'I have 42 cukes in my belly' } as PickleStep,
          TestStepResultStatus.PASSED
        )
      ).to.eq('Given I have 42 cukes in my belly...........................................passed')
    })
  })
})
