import type { NamingStrategy } from '@cucumber/query'

export interface Options {
  suiteName?: string
  testClassName?: string
  testNamingStrategy?: NamingStrategy
}
