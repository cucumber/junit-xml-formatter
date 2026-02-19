import { Envelope } from '@cucumber/messages'

import { JUnitXmlPrinter } from './JUnitXmlPrinter'
import { Options } from './types'

export const plugin = {
  type: 'formatter',
  formatter({
    options,
    on,
    write,
  }: {
    options: Options
    on: (type: 'message', handler: (message: Envelope) => void) => void
    write: (content: string) => void
  }) {
    const printer = new JUnitXmlPrinter(options, write)
    on('message', (message) => printer.update(message))
  },
  optionsKey: 'junit',
}
