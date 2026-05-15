# frozen_string_literal: true

module Cucumber
  module JunitXmlFormatter
    # rubocop:disable Metrics/ClassLength
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
      ZERO_DURATION = Cucumber::Messages::Duration.new(seconds: 0, nanos: 0)
      SCENARIO_WITH_NO_STEPS = Cucumber::Messages::TestStepResult.new(
        duration: ZERO_DURATION,
        message: nil,
        status: 'PASSED',
        exception: nil
      )

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

      def test_case_name_for(element)
        pickle = element.respond_to?(:test_case_id) ? query.find_pickle_by(element) : element
        lineage = query.find_lineage_by(pickle)
        raise ArgumentError, "expected to find lineage for pickle #{pickle.id}" unless lineage

        test_naming_strategy.reduce(lineage, pickle)
      end

      def test_case_classname_for(element)
        pickle = element.respond_to?(:test_case_id) ? query.find_pickle_by(element) : element

        test_class_name || query.find_lineage_by(pickle)&.dig(:feature)&.name || pickle.uri
      end

      def build_report
        xml = +%(<?xml version="1.0" encoding="UTF-8"?>\n)
        xml << "<testsuite#{attributes(testsuite_attributes)}>\n"
        ordered_test_case_started.each do |test_case_started|
          xml << testcase_element(test_case_started)
        end
        xml << "</testsuite>\n"
      end

      # rubocop:disable Metrics/MethodLength
      def testsuite_attributes
        attrs = {
          name: suite_name,
          time: duration_seconds(query.find_test_run_duration),
          tests: query.count_test_cases_started,
          skipped: skipped_count,
          failures: failure_count,
          errors: 0
        }
        timestamp = formatted_timestamp
        attrs[:timestamp] = timestamp if timestamp
        attrs
      end

      # rubocop:enable Metrics/MethodLength

      def testcase_element(test_case_started)
        xml = "<testcase#{attributes(testcase_attributes(test_case_started))}>\n"
        xml << non_passed_element(test_case_started)
        xml << system_out_element(test_case_started)
        xml << "</testcase>\n"
      end

      def testcase_attributes(test_case_started)
        {
          classname: test_case_classname_for(test_case_started),
          name: test_case_name_for(test_case_started),
          time: duration_seconds(query.find_test_case_duration_by(test_case_started))
        }
      end

      def non_passed_element(test_case_started)
        result = test_case_status(test_case_started)
        status = result.status
        return +'' if status == 'PASSED'

        element_name = status == 'SKIPPED' ? 'skipped' : 'failure'
        attrs = failure_attributes(result, status)
        cdata = result.exception&.stack_trace || result.message
        return "<#{element_name}#{attributes(attrs)}/>\n" unless cdata

        "<#{element_name}#{attributes(attrs)}>\n#{cdata_section(cdata)}\n</#{element_name}>\n"
      end

      def failure_attributes(result, status)
        return {} if status == 'SKIPPED'

        attrs = {}
        attrs[:type] = result.exception.type if result.exception&.type
        attrs[:message] = result.exception.message if result.exception&.message
        attrs
      end

      def system_out_element(test_case_started)
        steps_and_results = steps_and_results(test_case_started)
        return +'' if steps_and_results.empty?

        "<system-out>#{cdata_section(step_result_list(steps_and_results))}</system-out>\n"
      end

      def steps_and_results(test_case_started)
        query.find_test_step_finished_and_test_step_by(test_case_started).filter_map do |test_step_finished, test_step|
          next unless test_step.respond_to?(:pickle_step_id) && test_step.pickle_step_id

          [test_step_text(test_step), test_step_finished.test_step_result.status.downcase]
        end
      end

      def test_step_text(test_step)
        pickle_step = query.find_pickle_step_by(test_step)
        step = query.find_step_by(pickle_step)
        "#{step&.keyword}#{pickle_step&.text}"
      end

      def step_result_list(steps_and_results)
        "\n#{steps_and_results.map { |text, result| step_result_line(text, result) }.join}"
      end

      def step_result_line(text, result)
        dots = '.' * [0, 76 - 2 - text.length].max
        "#{text}..#{dots}#{result}\n"
      end

      def test_case_status(test_case_started)
        query.find_most_severe_test_step_result_by(test_case_started) || SCENARIO_WITH_NO_STEPS
      end

      def ordered_test_case_started
        query.find_all_test_case_started_order_by(
          ->(query, test_case_started) { query.find_pickle_by(test_case_started) },
          method(:compare_pickles)
        )
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

      def formatted_timestamp
        timestamp = query.find_test_run_started&.timestamp
        return nil unless timestamp

        format_timestamp(timestamp)
      end

      def duration_seconds(duration)
        return 0 unless duration

        duration.seconds + (duration.nanos / 1_000_000_000.0)
      end

      def format_timestamp(timestamp)
        Time.at(timestamp.seconds, timestamp.nanos, :nanosecond).utc.iso8601(3).sub('.000Z', 'Z')
      end

      def attributes(attrs)
        attrs.map { |name, value| %( #{name}="#{escape_attribute(value)}") }.join
      end

      def escape_attribute(value)
        escape_illegal_xml_chars(value.to_s)
          .gsub('&', '&amp;')
          .gsub('"', '&quot;')
          .gsub('<', '&lt;')
          .gsub('>', '&gt;')
      end

      def cdata_section(value)
        escape_illegal_xml_chars(value.to_s).split(/(?<=\]\])(?=>)/, -1).map { |part| "<![CDATA[#{part}]]>" }.join
      end

      def escape_illegal_xml_chars(value)
        value.each_codepoint.map do |codepoint|
          legal_xml_char?(codepoint) ? codepoint.chr(Encoding::UTF_8) : "&##{codepoint};"
        end.join
      end

      def legal_xml_char?(codepoint)
        codepoint == 0x9 || codepoint == 0xA || codepoint == 0xD ||
          codepoint.between?(0x20, 0xD7FF) ||
          codepoint.between?(0xE000, 0xFFFD) ||
          codepoint.between?(0x10000, 0x10FFFF)
      end
    end
    # rubocop:enable Metrics/ClassLength

    JUnitXmlPrinter = MessagesToJunitXmlWriter
  end
end
