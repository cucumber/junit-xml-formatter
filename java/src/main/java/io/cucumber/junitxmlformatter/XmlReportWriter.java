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

    //refactor: rename the private field write to xmlWriter for better readability
    void writeXmlReport(Writer out) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        EscapingXmlStreamWriter xmlWriter = new EscapingXmlStreamWriter(factory.createXMLStreamWriter(out));
        xmlWriter.writeStartDocument("UTF-8", "1.0");
        xmlWriter.writeNewLine();
        writeTestsuite(xmlWriter);
        xmlWriter.writeEndDocument();
        xmlWriter.flush();
    }

    private void writeTestsuite(EscapingXmlStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeStartElement("testsuite");
        writeSuiteAttributes(xmlWriter);
        xmlWriter.writeNewLine();

        for (TestCaseStarted testCaseStarted : data.getAllTestCaseStarted()) {
            writeTestcase(xmlWriter, testCaseStarted);
        }

        xmlWriter.writeEndElement();
        xmlWriter.writeNewLine();
    }

    private void writeSuiteAttributes(EscapingXmlStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeAttribute("name", data.getTestSuiteName());
        xmlWriter.writeAttribute("time", String.valueOf(data.getSuiteDurationInSeconds()));

        Map<TestStepResultStatus, Long> counts = data.getTestCaseStatusCounts();

        xmlWriter.writeAttribute("tests", String.valueOf(data.getTestCaseCount()));
        xmlWriter.writeAttribute("skipped", String.valueOf(counts.get(SKIPPED)));
        xmlWriter.writeAttribute("failures", String.valueOf(countFailures(counts)));
        xmlWriter.writeAttribute("errors", "0");

        Optional<String> testRunStartedAt = data.getTestRunStartedAt();
        if (testRunStartedAt.isPresent()) {
            xmlWriter.writeAttribute("timestamp", testRunStartedAt.get());
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

    private void writeTestcase(EscapingXmlStreamWriter xmlWriter, TestCaseStarted testCaseStarted) throws XMLStreamException {
        xmlWriter.writeStartElement("testcase");
        writeTestCaseAttributes(xmlWriter, testCaseStarted);
        xmlWriter.writeNewLine();
        writeNonPassedElement(xmlWriter, testCaseStarted);
        writeStepAndResultList(xmlWriter, testCaseStarted);
        xmlWriter.writeEndElement();
        xmlWriter.writeNewLine();
    }

    private void writeTestCaseAttributes(EscapingXmlStreamWriter xmlWriter, TestCaseStarted testCaseStarted) throws XMLStreamException {
        xmlWriter.writeAttribute("classname", data.getTestClassName(testCaseStarted));
        xmlWriter.writeAttribute("name", data.getTestName(testCaseStarted));
        xmlWriter.writeAttribute("time", String.valueOf(data.getDurationInSeconds(testCaseStarted)));
    }

    private void writeNonPassedElement(EscapingXmlStreamWriter xmlWriter, TestCaseStarted testCaseStarted) throws XMLStreamException {
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
            xmlWriter.writeStartElement(elementName);
        } else {
            xmlWriter.writeEmptyElement(elementName);
        }

        if (status != SKIPPED && exceptionType.isPresent()) {
            xmlWriter.writeAttribute("type", exceptionType.get());
        }
        if (status != SKIPPED && exceptionMessage.isPresent()) {
            xmlWriter.writeAttribute("message", exceptionMessage.get());
        }
        if (hasMessageOrStackTrace) {
            if (exceptionStackTrace.isPresent()) {
                xmlWriter.writeNewLine();
                xmlWriter.writeCData(exceptionStackTrace.get());
                xmlWriter.writeNewLine();
            } else {
                // Fall back to message for older implementations
                // that put the stack trace in the message
                xmlWriter.writeNewLine();
                xmlWriter.writeCData(message.get());
                xmlWriter.writeNewLine();
            }
        }

        if (hasMessageOrStackTrace) {
            xmlWriter.writeEndElement();
        }
        xmlWriter.writeNewLine();
    }

    private void writeStepAndResultList(EscapingXmlStreamWriter xmlWriter, TestCaseStarted testCaseStarted) throws XMLStreamException {
        List<Map.Entry<String, String>> results = data.getStepsAndResult(testCaseStarted);
        if (results.isEmpty()) {
            return;
        }
        xmlWriter.writeStartElement("system-out");
        xmlWriter.writeCData(createStepResultList(results));
        xmlWriter.writeEndElement();
        xmlWriter.writeNewLine();
    }

    private static String createStepResultList(List<Map.Entry<String, String>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        results.forEach(r -> {
            String stepText = r.getKey();
            String status = r.getValue();
            sb.append(stepText);
            // minimum of two dots between step text and status.
            sb.append("..");
            // pad to 76 characters per line, minus the two mandatory dots and step text
            sb.append(".".repeat(Math.max(0, 76 - 2 - stepText.length())));
            sb.append(status);
            sb.append("\n");
        });
        return sb.toString();
    }
}