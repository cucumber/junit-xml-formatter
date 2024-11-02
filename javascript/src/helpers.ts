import {
  Duration,
  PickleStep,
  Step,
  TestStepResultStatus,
  TimeConversion,
} from '@cucumber/messages'

export function durationToSeconds(duration?: Duration) {
  if (!duration) {
    return 0
  }
  return TimeConversion.durationToMilliseconds(duration) / 1000
}

export function countStatuses(
  statuses: Record<TestStepResultStatus, number>,
  predicate: (status: TestStepResultStatus) => boolean = () => true
) {
  return Object.entries(statuses)
    .filter(([status]) => predicate(status as TestStepResultStatus))
    .reduce((prev, [, curr]) => prev + curr, 0)
}

export function formatStep(step: Step, pickleStep: PickleStep, status: TestStepResultStatus) {
  let text = `${step.keyword.trim()} ${pickleStep.text.trim()}.`
  do {
    text += '.'
  } while (text.length < 76)
  return text + status.toLowerCase()
}
