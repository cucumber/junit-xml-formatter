# frozen_string_literal: true

require 'cucumber/messages/helpers/ndjson_to_message_enumerator'
require 'nokogiri'
require 'stringio'

SHARED_TESTDATA_DIR = File.expand_path('../../../../testdata/src', __dir__)
SHARED_DEFAULT_FIXTURES = Dir[File.join(SHARED_TESTDATA_DIR, '*.ndjson')].freeze
SHARED_CUSTOM_NAMING_FIXTURE = File.join(SHARED_TESTDATA_DIR, 'examples-tables.ndjson')
JENKINS_SCHEMA = File.expand_path('../../../../jenkins-junit.xsd', __dir__)
SUREFIRE_SCHEMA = File.expand_path('../../../../surefire-test-report-3.0.2.xsd', __dir__)

RSpec.describe Cucumber::JunitXmlFormatter do
  def read_envelopes(path)
    File.open(path, 'r') do |io|
      Cucumber::Messages::Helpers::NdjsonToMessageEnumerator.new(io).to_a
    end
  end

  def write_report(envelopes, **options)
    stream = StringIO.new
    writer = build_writer(stream, **options)

    envelopes.each { |envelope| write_envelope(writer, envelope) }
    writer.close if writer.respond_to?(:close)

    stream.string
  end

  def build_writer(stream, **options)
    if defined?(Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter)
      Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter.new(stream:, **options)
    else
      pending('Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter is not implemented yet')
      raise NameError, 'Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter'
    end
  end

  def write_envelope(writer, envelope)
    if writer.respond_to?(:write)
      writer.write(envelope)
    elsif writer.respond_to?(:update)
      writer.update(envelope)
    else
      raise NoMethodError, 'expected writer to respond to #write or #update'
    end
  end

  def expect_xml_to_match(actual, expected_path)
    expect(normalize_xml(actual)).to eq(normalize_xml(File.read(expected_path)))
  end

  def normalize_xml(xml)
    xml.gsub(/>\s+</, '><').strip
  end

  def schema_validation_errors(xml, schema_path)
    schema = Nokogiri::XML::Schema(File.read(schema_path))
    document = Nokogiri::XML(xml, &:strict)

    schema.validate(document).map(&:message)
  end

  # rubocop:disable RSpec/LeakyLocalVariable
  SHARED_DEFAULT_FIXTURES.each do |fixture|
    name = File.basename(fixture, '.ndjson')
    expected = File.join(SHARED_TESTDATA_DIR, "#{name}.default.xml")

    it "matches #{File.basename(expected)}" do
      envelopes = read_envelopes(fixture)
      actual = write_report(envelopes)

      expect_xml_to_match(actual, expected)
    end
  end
  # rubocop:enable RSpec/LeakyLocalVariable

  it 'matches examples-tables.custom.xml with custom naming options' do
    envelopes = read_envelopes(SHARED_CUSTOM_NAMING_FIXTURE)
    actual = write_report(envelopes, **custom_naming_options)

    expect_xml_to_match(actual, File.join(SHARED_TESTDATA_DIR, 'examples-tables.custom.xml'))
  end

  it 'covers simulated parallel message ordering' do
    envelopes = read_envelopes(File.join(SHARED_TESTDATA_DIR, 'multiple-features-reversed.ndjson')).reverse
    actual = write_report(envelopes)

    expect_xml_to_match(actual, File.join(SHARED_TESTDATA_DIR, 'multiple-features-reversed.default.xml'))
  end

  SHARED_DEFAULT_FIXTURES.each do |fixture|
    name = File.basename(fixture, '.ndjson')

    it "generates Jenkins-valid XML for #{name}" do
      envelopes = read_envelopes(fixture)
      actual = write_report(envelopes)

      expect(schema_validation_errors(actual, JENKINS_SCHEMA)).to be_empty
    end
  end

  it 'generates Jenkins-valid XML with custom naming options' do
    envelopes = read_envelopes(SHARED_CUSTOM_NAMING_FIXTURE)
    actual = write_report(envelopes, **custom_naming_options)

    expect(schema_validation_errors(actual, JENKINS_SCHEMA)).to be_empty
  end

  it 'documents the known Surefire schema timestamp incompatibility' do
    envelopes = read_envelopes(File.join(SHARED_TESTDATA_DIR, 'minimal.ndjson'))
    actual = write_report(envelopes)

    expect(schema_validation_errors(actual, SUREFIRE_SCHEMA)).to include(
      a_string_including("attribute 'timestamp': The attribute 'timestamp' is not allowed")
    )
  end

  def custom_naming_options
    {
      suite_name: 'Cucumber Suite',
      test_class_name: 'Cucumber Class',
      test_naming_strategy: :long
    }
  end
end
