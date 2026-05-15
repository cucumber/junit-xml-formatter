# frozen_string_literal: true

module Cucumber
  module JunitXmlFormatter
    # rubocop:disable Metrics/ClassLength
    # Collects Cucumber Messages and exposes the report model used by the XML writer.
    class XmlReportData
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
      ZERO_DURATION = Cucumber::Messages::Duration.new(seconds: 0, nanos: 0)
      SCENARIO_WITH_NO_STEPS = Cucumber::Messages::TestStepResult.new(
        duration: ZERO_DURATION,
        message: nil,
        status: 'PASSED',
        exception: nil
      )

      attr_reader :query, :suite_name, :test_class_name, :test_naming_strategy

      def initialize(suite_name: DEFAULT_SUITE_NAME, test_class_name: nil, test_naming_strategy: nil)
        @suite_name = suite_name
        @test_class_name = test_class_name
        @test_naming_strategy = resolve_test_naming_strategy(test_naming_strategy)
        @query = Cucumber::Query::Query.new
      end

      def collect(envelope)
        query.update(envelope)
        self
      end
      alias update collect

      def suite_duration_seconds = duration_seconds(query.find_test_run_duration)

      def test_case_duration_seconds(test_case_started)
        duration_seconds(query.find_test_case_duration_by(test_case_started))
      end

      def test_case_status_counts = query.count_most_severe_test_step_result_status

      def test_case_count = query.count_test_cases_started

      def skipped_count = test_case_status_counts.fetch('SKIPPED', 0)

      def failure_count
        test_case_status_counts.sum { |status, count| NON_FAILURE_STATUSES.include?(status) ? 0 : count }
      end

      def errors_count = 0

      def test_case_name(test_case_started)
        pickle = pickle_for(test_case_started)
        lineage = query.find_lineage_by(pickle)

        lineage ? test_naming_strategy.reduce(lineage, pickle) : pickle.name
      end

      def test_case_classname(test_case_started)
        return test_class_name if test_class_name

        query.find_lineage_by(test_case_started)&.dig(:feature)&.name || pickle_for(test_case_started).uri
      end

      def steps_and_results(test_case_started)
        query.find_test_step_finished_and_test_step_by(test_case_started).filter_map do |test_step_finished, test_step|
          next unless test_step.respond_to?(:pickle_step_id) && test_step.pickle_step_id

          [test_step_text(test_step), test_step_finished.test_step_result.status.downcase]
        end
      end

      def ordered_test_cases
        query.find_all_test_case_started_order_by(
          ->(query, test_case_started) { query.find_pickle_by(test_case_started) },
          method(:compare_pickles)
        )
      end

      def test_case_status(test_case_started)
        query.find_most_severe_test_step_result_by(test_case_started) || SCENARIO_WITH_NO_STEPS
      end

      def test_run_started_at
        timestamp = query.find_test_run_started&.timestamp
        return nil unless timestamp

        Time.at(timestamp.seconds, timestamp.nanos, :nanosecond).utc.iso8601(3).sub('.000Z', 'Z')
      end

      private

      def pickle_for(test_case_started)
        query.find_pickle_by(test_case_started) ||
          raise(ArgumentError, "expected to find pickle for #{test_case_started.id}")
      end

      def test_step_text(test_step)
        pickle_step = query.find_pickle_step_by(test_step)
        step = query.find_step_by(pickle_step)
        "#{step&.keyword}#{pickle_step&.text}"
      end

      def compare_pickles(left, right)
        uri_comparison = left.uri <=> right.uri
        return uri_comparison unless uri_comparison.zero?

        compare_locations(query.find_location_of(left), query.find_location_of(right))
      end

      def compare_locations(left, right)
        return 0 if left.nil? && right.nil?
        return -1 if left.nil?
        return 1 if right.nil?

        [left.line, left.column || 0] <=> [right.line, right.column || 0]
      end

      def resolve_test_naming_strategy(strategy)
        return DEFAULT_NAMING_STRATEGY unless strategy
        return NAMING_STRATEGIES.fetch(strategy) if strategy.is_a?(Symbol)
        return strategy if strategy.respond_to?(:reduce)

        raise ArgumentError, 'test_naming_strategy must be a Symbol or respond to #reduce'
      end

      def duration_seconds(duration)
        return 0 unless duration

        duration.seconds + (duration.nanos / 1_000_000_000.0)
      end
    end
    # rubocop:enable Metrics/ClassLength
  end
end
