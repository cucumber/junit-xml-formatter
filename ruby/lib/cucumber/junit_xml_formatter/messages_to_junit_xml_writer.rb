# frozen_string_literal: true

module Cucumber
  module JunitXmlFormatter
    # Streaming public API for converting Cucumber Messages envelopes to a JUnit XML report.
    #
    # The writer mirrors the JavaScript JUnitXmlPrinter message idiom while using Ruby
    # stream and keyword conventions. Feed Cucumber::Messages::Envelope instances with
    # #write (or the #update alias). The report is finalized when a testRunFinished
    # envelope is seen, or when #close is called.
    class MessagesToJunitXmlWriter
      DEFAULT_SUITE_NAME = 'Cucumber'
      DEFAULT_NAMING_STRATEGY = Cucumber::Query.naming_strategy(
        Cucumber::Query::NAMING_STRATEGY_LENGTH_LONG,
        Cucumber::Query::NAMING_STRATEGY_FEATURE_NAME_EXCLUDE,
        Cucumber::Query::NAMING_STRATEGY_EXAMPLE_NAME_NUMBER_AND_PICKLE_IF_PARAMETERIZED
      )
      NAMING_STRATEGIES = {
        long: Cucumber::Query.naming_strategy(Cucumber::Query::NAMING_STRATEGY_LENGTH_LONG),
        short: Cucumber::Query.naming_strategy(Cucumber::Query::NAMING_STRATEGY_LENGTH_SHORT),
        default: DEFAULT_NAMING_STRATEGY
      }.freeze
      NON_FAILURE_STATUSES = %w[PASSED SKIPPED].freeze

      attr_reader :stream, :suite_name, :test_class_name, :test_naming_strategy, :query

      def initialize(stream:, suite_name: DEFAULT_SUITE_NAME, test_class_name: nil, test_naming_strategy: nil)
        @stream = stream
        @suite_name = suite_name
        @test_class_name = test_class_name
        @test_naming_strategy = resolve_test_naming_strategy(test_naming_strategy)
        @query = Cucumber::Query::Query.new
        @closed = false
      end

      def write(envelope)
        raise IOError, 'cannot write to a closed JUnit XML writer' if closed?

        query.update(envelope)
        close if envelope.respond_to?(:test_run_finished) && envelope.test_run_finished
        self
      end
      alias update write

      def close
        return self if closed?

        stream.write(build_report)
        @closed = true
        self
      end

      def closed? = @closed

      private

      def test_case_name_for(pickle)
        lineage = query.find_lineage_by(pickle)
        raise ArgumentError, "expected to find lineage for pickle #{pickle.id}" unless lineage

        test_naming_strategy.reduce(lineage, pickle)
      end

      def test_case_classname_for(pickle)
        test_class_name || query.find_lineage_by(pickle)&.dig(:feature)&.name || pickle.uri
      end

      def build_report
        <<~XML.chomp
          <?xml version="1.0"?>
          <testsuite name="#{escape_attribute(suite_name)}" time="#{duration_seconds(query.find_test_run_duration)}" tests="#{query.count_test_cases_started}" skipped="#{skipped_count}" failures="#{failure_count}" errors="0"#{timestamp_attribute}/>
        XML
      end

      def skipped_count = statuses.fetch('SKIPPED', 0)

      def failure_count
        statuses.sum { |status, count| NON_FAILURE_STATUSES.include?(status) ? 0 : count }
      end

      def statuses = @statuses ||= query.count_most_severe_test_step_result_status

      def resolve_test_naming_strategy(strategy)
        return DEFAULT_NAMING_STRATEGY unless strategy
        return NAMING_STRATEGIES.fetch(strategy) if strategy.is_a?(Symbol)
        return strategy if strategy.respond_to?(:reduce)

        raise ArgumentError, 'test_naming_strategy must be a Symbol or respond to #reduce'
      end

      def timestamp_attribute
        timestamp = query.find_test_run_started&.timestamp
        return '' unless timestamp

        %( timestamp="#{format_timestamp(timestamp)}")
      end

      def duration_seconds(duration)
        return 0 unless duration

        duration.seconds + (duration.nanos / 1_000_000_000.0)
      end

      def format_timestamp(timestamp)
        Time.at(timestamp.seconds, timestamp.nanos, :nanosecond).utc.iso8601(3)
      end

      def escape_attribute(value)
        value.to_s.gsub('&', '&amp;').gsub('"', '&quot;').gsub('<', '&lt;').gsub('>', '&gt;')
      end
    end

    JUnitXmlPrinter = MessagesToJunitXmlWriter
  end
end
