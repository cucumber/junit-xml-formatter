# frozen_string_literal: true

require 'cucumber/messages/helpers/ndjson_to_message_enumerator'

RSpec.describe Cucumber::JunitXmlFormatter::XmlReportData do
  # rubocop:disable RSpec/ExampleLength
  it 'computes suite counts, duration and timestamp' do
    data = collect_fixture('minimal')

    expect(data.suite_duration_seconds).to eq(0.005)
    expect(data.test_case_count).to eq(1)
    expect(data.skipped_count).to eq(0)
    expect(data.failure_count).to eq(0)
    expect(data.errors_count).to eq(0)
    expect(data.test_run_started_at).to eq('1970-01-01T00:00:00Z')
  end

  it 'maps severe statuses to skipped and failures' do
    data = collect_fixture('all-statuses')

    expect(data.test_case_status_counts).to include(
      'PASSED' => 1,
      'SKIPPED' => 1,
      'FAILED' => 1,
      'AMBIGUOUS' => 1,
      'UNDEFINED' => 1,
      'PENDING' => 1
    )
    expect(data.skipped_count).to eq(1)
    expect(data.failure_count).to eq(4)
  end

  it 'computes testcase class, name, duration and step output data' do
    data = collect_fixture('minimal')
    test_case_started = data.ordered_test_cases.fetch(0)

    expect(data.test_case_classname(test_case_started)).to eq('minimal')
    expect(data.test_case_name(test_case_started)).to eq('cukes')
    expect(data.test_case_duration_seconds(test_case_started)).to eq(0.003)
    expect(data.steps_and_results(test_case_started)).to eq([['Given I have 42 cukes in my belly', 'passed']])
  end

  it 'uses custom class names and long naming strategy' do
    data = collect_fixture('examples-tables', test_class_name: 'Cucumber Class', test_naming_strategy: :long)
    test_case_started = data.ordered_test_cases.fetch(0)

    expect(data.test_case_classname(test_case_started)).to eq('Cucumber Class')
    expect(data.test_case_name(test_case_started)).to eq(
      'Examples Tables - Eating cucumbers - These are passing - #1.1'
    )
  end

  it 'chooses the most severe result from retries' do
    data = collect_fixture('retry')

    expect(data.test_case_status_counts).to include('FAILED' => 1, 'PASSED' => 3)
    expect(data.ordered_test_cases.map { |test_case_started| data.test_case_status(test_case_started).status })
      .to contain_exactly('FAILED', 'PASSED', 'PASSED', 'PASSED')
  end

  it 'orders testcases by pickle URI and location, not message arrival order' do
    data = collect_fixture('multiple-features-reversed', reverse: true)

    expected_classnames = (['First feature'] * 3) + (['Second feature'] * 3) + (['Third feature'] * 3)

    expect(data.ordered_test_cases.map { |test_case_started| data.test_case_classname(test_case_started) })
      .to eq(expected_classnames)
    expect(data.ordered_test_cases.map { |test_case_started| data.test_case_name(test_case_started) })
      .to eq(%w[First Second Third].map { |ordinal| "#{ordinal} scenario" } * 3)
  end
  # rubocop:enable RSpec/ExampleLength

  def collect_fixture(name, reverse: false, **options)
    described_class.new(**options).tap do |data|
      envelopes = read_envelopes(name)
      envelopes = envelopes.reverse if reverse
      envelopes.each { |envelope| data.collect(envelope) }
    end
  end

  def read_envelopes(suite)
    path = File.expand_path("../../../testdata/src/#{suite}.ndjson", __dir__)
    File.open(path, 'r') do |io|
      Cucumber::Messages::Helpers::NdjsonToMessageEnumerator.new(io).to_a
    end
  end
end
