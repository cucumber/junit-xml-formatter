# frozen_string_literal: true

require 'cucumber/messages/helpers/ndjson_to_message_enumerator'
require 'stringio'

RSpec.describe Cucumber::JunitXmlFormatter do
  it 'exposes the package version' do
    expect(described_class::VERSION).to eq('0.13.3')
  end

  describe Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter do
    let(:stream) { StringIO.new }

    it 'is the public printer API exported by the package' do
      expect(Cucumber::JunitXmlFormatter::JUnitXmlPrinter).to be(described_class)
    end

    it 'accepts suite naming options' do
      writer = described_class.new(stream:, suite_name: 'Cucumber Suite', test_class_name: 'Cucumber Class')

      expect(writer.suite_name).to eq('Cucumber Suite')
      expect(writer.test_class_name).to eq('Cucumber Class')
    end

    it 'accepts a test naming strategy option' do
      writer = described_class.new(stream:, test_naming_strategy: :long)

      expect(writer.test_naming_strategy).to respond_to(:reduce)
    end

    it 'uses default JUnit naming semantics from the shared examples-table fixture' do
      expect(test_case_names(examples_table_writer)).to eq(default_examples_table_names)
    end

    it 'uses custom long naming semantics from the shared examples-table fixture' do
      writer = examples_table_writer(test_naming_strategy: :long)

      expect(test_case_names(writer)).to start_with(*long_examples_table_names)
    end

    it 'supports the streaming method names' do
      writer = described_class.new(stream:)

      expect(writer).to respond_to(:write, :update, :close)
    end

    it 'finalizes to the configured stream' do
      writer = described_class.new(stream:, suite_name: 'Cucumber & Friends')

      expect(writer.close).to be(writer)
      expect(stream.string).to include('name="Cucumber &amp; Friends"')
    end

    it 'rejects writes after finalization' do
      writer = described_class.new(stream:)
      writer.close

      expect { writer.write(nil) }.to raise_error(IOError, 'cannot write to a closed JUnit XML writer')
    end

    it 'matches the representative minimal shared expected XML' do
      writer = fixture_writer('minimal')
      writer.close

      expect(normalize_xml(stream.string)).to eq(normalize_xml(read_expected_xml('minimal')))
    end

    it 'writes failure attributes and uses stack trace CDATA when available' do
      writer = fixture_writer('stack-traces')
      writer.close

      expect(stream.string).to include('<failure type="Error" message="BOOM">')
      expect(stream.string).to include('<![CDATA[Error: BOOM')
    end

    it 'writes empty failure elements when no failure message or stack trace is available' do
      writer = fixture_writer('undefined')
      writer.close

      expect(stream.string).to include('<failure/>')
    end

    # rubocop:disable RSpec/ExampleLength
    it 'writes skipped elements and skipped step output' do
      writer = fixture_writer('skipped')
      writer.close

      expect(stream.string).to include('<skipped/>')
      expect(stream.string).to include(
        'And a step that is skipped..................................................skipped'
      )
    end

    # rubocop:enable RSpec/ExampleLength

    it 'escapes illegal XML characters in attributes like the Java writer' do
      writer = described_class.new(stream:, suite_name: "Hello \u0000 world")
      writer.close

      expect(stream.string).to include('name="Hello &amp;#0; world"')
    end

    it 'splits CDATA terminators' do
      writer = described_class.new(stream:)

      expect(writer.__send__(:cdata_section, 'Hello <![CDATA[ cdata ]]> world'))
        .to eq('<![CDATA[Hello <![CDATA[ cdata ]]]]><![CDATA[> world]]>')
    end

    def examples_table_writer(**options)
      fixture_writer('examples-tables', **options)
    end

    def fixture_writer(suite, **options)
      described_class.new(stream:, **options).tap do |writer|
        read_envelopes(suite).each { |envelope| writer.update(envelope) }
      end
    end

    def read_envelopes(suite)
      path = File.expand_path("../../../testdata/src/#{suite}.ndjson", __dir__)
      File.open(path, 'r') do |io|
        Cucumber::Messages::Helpers::NdjsonToMessageEnumerator.new(io).to_a
      end
    end

    def read_expected_xml(suite)
      File.read(File.expand_path("../../../testdata/src/#{suite}.default.xml", __dir__))
    end

    def normalize_xml(xml)
      xml.gsub(/>\s+</, '><').strip
    end

    def test_case_names(writer)
      writer.data.ordered_test_cases.map { |test_case_started| writer.data.test_case_name(test_case_started) }
    end

    def default_examples_table_names
      [
        'Eating cucumbers - These are passing - #1.1',
        'Eating cucumbers - These are passing - #1.2',
        'Eating cucumbers - These are failing - #2.1',
        'Eating cucumbers - These are failing - #2.2',
        'Eating cucumbers with <friends> friends - #1.1: Eating cucumbers with 11 friends',
        'Eating cucumbers with <friends> friends - #1.2: Eating cucumbers with 1 friends',
        'Eating cucumbers with <friends> friends - #1.3: Eating cucumbers with 0 friends'
      ]
    end

    def long_examples_table_names
      [
        'Examples Tables - Eating cucumbers - These are passing - #1.1',
        'Examples Tables - Eating cucumbers - These are passing - #1.2'
      ]
    end
  end
end
