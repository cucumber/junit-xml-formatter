import fs from 'node:fs'
import * as path from 'node:path'
import { pipeline, Writable } from 'node:stream'
import util from 'node:util'

import { NdjsonToMessageStream } from '@cucumber/message-streams'
import { Envelope } from '@cucumber/messages'
import { expect, use } from 'chai'
import chaiXml from 'chai-xml'
import { globbySync } from 'globby'

import formatter from './index.js'

const asyncPipeline = util.promisify(pipeline)
use(chaiXml)

describe('Acceptance Tests', async function() {
  this.timeout(10_000)

  const ndjsonFiles = globbySync(`*.ndjson`, {
    cwd: new URL(path.join(path.dirname(import.meta.url), '../../testdata')),
    absolute: true,
  })

  for (const ndjsonFile of ndjsonFiles) {
    const [suiteName] = path.basename(ndjsonFile).split('.')
    it(suiteName, async () => {
      let emit: (message: Envelope) => void
      let content = ''
      formatter.formatter({
        options: {},
        on(type, handler) {
          emit = handler
        },
        write: (chunk) => {
          content += chunk
        },
      })

      await asyncPipeline(
        fs.createReadStream(ndjsonFile, { encoding: 'utf-8' }),
        new NdjsonToMessageStream(),
        new Writable({
          objectMode: true,
          write(envelope: Envelope, _: BufferEncoding, callback) {
            emit(envelope)
            callback()
          },
        }),
      )

      const expectedXml = fs.readFileSync(ndjsonFile.replace('.ndjson', '.xml'), {
        encoding: 'utf-8',
      })
      expect(content).xml.to.be.valid()
      expect(content).xml.to.deep.eq(expectedXml)
    })
  }
}).timeout('5s')
