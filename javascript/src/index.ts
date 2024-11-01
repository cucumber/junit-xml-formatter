import { Envelope } from '@cucumber/messages'

export default {
  type: 'formatter',
  formatter({
    on,
    write,
  }: {
    on: (type: 'message', handler: (message: Envelope) => void) => void
    write: (content: string) => void
  }) {
    on('message', (message) => {})
  },
}
