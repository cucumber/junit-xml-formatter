# frozen_string_literal: true

module Cucumber
  module JunitXmlFormatter
    # rubocop:disable Metrics/ClassLength
    # Streaming public API for converting Cucumber Messages envelopes to a JUnit XML report.
    #
    # Feed Cucumber::Messages::Envelope instances with #write (or the #update alias). The
    # report is finalized when #close is called.
    class MessagesToJunitXmlWriter
      attr_reader :stream, :data

      def initialize(stream:, suite_name: XmlReportData::DEFAULT_SUITE_NAME,
                     test_class_name: nil, test_naming_strategy: nil)
        @stream = stream
        @data = XmlReportData.new(suite_name:, test_class_name:, test_naming_strategy:)
        @closed = false
      end

      def suite_name = data.suite_name

      def test_class_name = data.test_class_name

      def test_naming_strategy = data.test_naming_strategy

      def query = data.query

      def write(envelope)
        raise IOError, 'cannot write to a closed JUnit XML writer' if closed?

        data.collect(envelope)
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

      def build_report
        xml = +%(<?xml version="1.0" encoding="UTF-8"?>\n)
        xml << "<testsuite#{attributes(testsuite_attributes)}>\n"
        data.ordered_test_cases.each do |test_case_started|
          xml << testcase_element(test_case_started)
        end
        xml << "</testsuite>\n"
      end

      # rubocop:disable Metrics/AbcSize
      def testsuite_attributes
        attrs = {
          name: data.suite_name,
          time: data.suite_duration_seconds,
          tests: data.test_case_count,
          skipped: data.skipped_count,
          failures: data.failure_count,
          errors: data.errors_count
        }
        attrs[:timestamp] = data.test_run_started_at if data.test_run_started_at
        attrs
      end
      # rubocop:enable Metrics/AbcSize

      def testcase_element(test_case_started)
        xml = "<testcase#{attributes(testcase_attributes(test_case_started))}>\n"
        xml << non_passed_element(test_case_started)
        xml << system_out_element(test_case_started)
        xml << "</testcase>\n"
      end

      def testcase_attributes(test_case_started)
        {
          classname: data.test_case_classname(test_case_started),
          name: data.test_case_name(test_case_started),
          time: data.test_case_duration_seconds(test_case_started)
        }
      end

      def non_passed_element(test_case_started)
        result = data.test_case_status(test_case_started)
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
        steps_and_results = data.steps_and_results(test_case_started)
        return +'' if steps_and_results.empty?

        "<system-out>#{cdata_section(step_result_list(steps_and_results))}</system-out>\n"
      end

      def step_result_list(steps_and_results)
        "\n#{steps_and_results.map { |text, result| step_result_line(text, result) }.join}"
      end

      def step_result_line(text, result)
        dots = '.' * [0, 76 - 2 - text.length].max
        "#{text}..#{dots}#{result}\n"
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
