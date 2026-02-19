import fs from 'node:fs'
import * as path from 'node:path'
import { Writable } from 'node:stream'
import { pipeline } from 'node:stream/promises'

import { NdjsonToMessageStream } from '@cucumber/message-streams'
import { Envelope } from '@cucumber/messages'
import { namingStrategy, NamingStrategyLength } from '@cucumber/query'
import { expect, use } from 'chai'
import chaiXml from 'chai-xml'
import globby from 'globby'

import { plugin } from './plugin'

use(chaiXml)

describe('Acceptance Tests', async function () {
  this.timeout(10_000)

  const ndjsonFiles = globby.sync(`*.ndjson`, {
    cwd: path.join(__dirname, '../../testdata/src'),
    absolute: true,
  })

  const testCases = ndjsonFiles.map((ndjsonFile) => {
    const [suiteName] = path.basename(ndjsonFile).split('.')
    return {
      suiteName,
      source: ndjsonFile,
      strategyName: 'default',
      options: {},
    }
  })

  testCases.push({
    suiteName: 'examples-tables',
    source: '../testdata/src/examples-tables.ndjson',
    strategyName: 'custom',
    options: {
      suiteName: 'Cucumber Suite',
      testClassName: 'Cucumber Class',
      testNamingStrategy: namingStrategy(NamingStrategyLength.LONG),
    },
  })

  for (const testCase of testCases) {
    it(testCase.suiteName + ' -> ' + testCase.strategyName, async () => {
      let emit: (message: Envelope) => void
      let content = ''
      plugin.formatter({
        options: testCase.options,
        on(type, handler) {
          emit = handler
        },
        write: (chunk) => {
          content += chunk
        },
      })

      await pipeline(
        fs.createReadStream(testCase.source, { encoding: 'utf-8' }),
        new NdjsonToMessageStream(),
        new Writable({
          objectMode: true,
          write(envelope: Envelope, _: BufferEncoding, callback) {
            emit(envelope)
            callback()
          },
        })
      )

      const expectedXml = fs.readFileSync(
        testCase.source.replace('.ndjson', '.' + testCase.strategyName + '.xml'),
        {
          encoding: 'utf-8',
        }
      )
      expect(content).xml.to.be.valid()
      expect(content).xml.to.deep.eq(expectedXml)
    })
  }
}).timeout('5s')
