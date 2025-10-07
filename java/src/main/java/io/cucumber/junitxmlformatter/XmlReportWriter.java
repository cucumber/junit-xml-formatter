package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Exception;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static io.cucumber.messages.types.TestStepResultStatus.SKIPPED;

class XmlReportWriter {
    private final XmlReportData data;

    XmlReportWriter(XmlReportData data) {
        this.data = data;
    }

    void writeXmlReport(Writer out) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        EscapingXmlStreamWriter writer = new EscapingXmlStreamWriter(factory.createXMLStreamWriter(out));
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeNewLine();
        writeTestsuite(writer);
        writer.writeEndDocument();
        writer.flush();
    }

    private void writeTestsuite(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("testsuite");
        writeSuiteAttributes(writer);
        writer.writeNewLine();

        for (TestCaseStarted testCaseStarted : data.getAllTestCaseStarted()) {
            writeTestcase(writer, testCaseStarted);
        }

        writer.writeEndElement();
        writer.writeNewLine();
    }

    private void writeSuiteAttributes(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute("name", data.getTestSuiteName());
        writer.writeAttribute("time", String.valueOf(data.getSuiteDurationInSeconds()));

        Map<TestStepResultStatus, Long> counts = data.getTestCaseStatusCounts();

        writer.writeAttribute("tests", String.valueOf(data.getTestCaseCount()));
        writer.writeAttribute("skipped", counts.get(SKIPPED).toString());
        writer.writeAttribute("failures", String.valueOf(countFailures(counts)));
        writer.writeAttribute("errors", "0");

        Optional<String> testRunStartedAt = data.getTestRunStartedAt();
        if (testRunStartedAt.isPresent()) {
            writer.writeAttribute("timestamp", testRunStartedAt.get());
        }
    }

    private static long countFailures(Map<TestStepResultStatus, Long> counts) {
        return createNotPassedNotSkippedSet().stream().mapToLong(counts::get).sum();
    }

    private static EnumSet<TestStepResultStatus> createNotPassedNotSkippedSet() {
        EnumSet<TestStepResultStatus> notPassedNotSkipped = EnumSet.allOf(TestStepResultStatus.class);
        notPassedNotSkipped.remove(PASSED);
        notPassedNotSkipped.remove(SKIPPED);
        return notPassedNotSkipped;
    }

    private void writeTestcase(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted) throws XMLStreamException {
        writer.writeStartElement("testcase");
        writeTestCaseAttributes(writer, testCaseStarted);
        writer.writeNewLine();
        writeNonPassedElement(writer, testCaseStarted);
        writeStepAndResultList(writer, testCaseStarted);
        writer.writeEndElement();
        writer.writeNewLine();
    }

    private void writeTestCaseAttributes(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted) throws XMLStreamException {
        writer.writeAttribute("classname", data.getTestClassName(testCaseStarted));
        writer.writeAttribute("name", data.getTestName(testCaseStarted));
        writer.writeAttribute("time", String.valueOf(data.getDurationInSeconds(testCaseStarted)));
    }

    private void writeNonPassedElement(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted) throws XMLStreamException {
        TestStepResult result = data.getTestCaseStatus(testCaseStarted);
        TestStepResultStatus status = result.getStatus();
        if (status == TestStepResultStatus.PASSED) {
            return;
        }

        String elementName = status == SKIPPED ? "skipped" : "failure";

        Optional<String> message = result.getMessage();
        Optional<String> exceptionType = result.getException().map(Exception::getType);
        Optional<String> exceptionMessage = result.getException().flatMap(Exception::getMessage);
        Optional<String> exceptionStackTrace = result.getException().flatMap(Exception::getStackTrace);

        boolean hasMessageOrStackTrace = message.isPresent() || exceptionStackTrace.isPresent();
        if (hasMessageOrStackTrace) {
            writer.writeStartElement(elementName);
        } else {
            writer.writeEmptyElement(elementName);
        }

        if (status != SKIPPED && exceptionType.isPresent()) {
            writer.writeAttribute("type", exceptionType.get());
        }
        if (exceptionMessage.isPresent()) {
            writer.writeAttribute("message", exceptionMessage.get());
        }
        if (hasMessageOrStackTrace) {
            if (exceptionStackTrace.isPresent()) {
                writer.writeNewLine();
                writer.writeCData(exceptionStackTrace.get());
                writer.writeNewLine();
            } else {
                // Fall back to message for older implementations
                // that put the stack trace in the message
                writer.writeNewLine();
                writer.writeCData(message.get());
                writer.writeNewLine();
            }
        }

        if (hasMessageOrStackTrace) {
            writer.writeEndElement();
        }
        writer.writeNewLine();
    }

    private void writeStepAndResultList(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted) throws XMLStreamException {
        List<Map.Entry<String, String>> results = data.getStepsAndResult(testCaseStarted);
        if (results.isEmpty()) {
            return;
        }
        writer.writeStartElement("system-out");
        writer.writeCData(createStepResultList(results));
        writer.writeEndElement();
        writer.writeNewLine();
    }

    private static String createStepResultList(List<Map.Entry<String, String>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        results.forEach(r -> {
            String stepText = r.getKey();
            String status = r.getValue();
            sb.append(stepText);
            sb.append(".");
            for (int i = 75 - stepText.length(); i > 0; i--) {
                sb.append(".");
            }
            sb.append(status);
            sb.append("\n");
        });
        return sb.toString();
    }
}