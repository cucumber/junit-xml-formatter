import { Duration, TestStepResultStatus, TimeConversion } from '@cucumber/messages'

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
