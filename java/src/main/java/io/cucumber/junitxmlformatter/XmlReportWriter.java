package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Exception;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.cucumber.messages.types.TestStepResultStatus.FAILED;
import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static io.cucumber.messages.types.TestStepResultStatus.SKIPPED;

class XmlReportWriter {
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    private final XmlReportData data;

    XmlReportWriter(XmlReportData data) {
        this.data = data;
    }

    void writeXmlReport(Writer out) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        EscapingXmlStreamWriter writer = new EscapingXmlStreamWriter(factory.createXMLStreamWriter(out));
        writer.writeStartDocument("UTF-8", "1.0");
        writer.newLine();
        writeTestsuite(data, writer);
        writer.writeEndDocument();
        writer.flush();
    }

    private void writeTestsuite(XmlReportData data, EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("testsuite");
        writeSuiteAttributes(writer);
        writer.newLine();

        for (String testCaseStartedId : data.testCaseStartedIds()) {
            writeTestcase(writer, testCaseStartedId);
        }

        writer.writeEndElement();
        writer.newLine();
    }

    private void writeSuiteAttributes(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute("name", "Cucumber");
        writer.writeAttribute("time", numberFormat.format(data.getSuiteDurationInSeconds()));

        Map<TestStepResultStatus, Long> counts = data.getTestCaseStatusCounts();

        writer.writeAttribute("tests", String.valueOf(data.getTestCaseCount()));
        writer.writeAttribute("skipped", counts.getOrDefault(SKIPPED, 0L).toString());
        writer.writeAttribute("failures", String.valueOf(countFailures(counts)));
        writer.writeAttribute("errors", "0");
    }

    private static long countFailures(Map<TestStepResultStatus, Long> counts) {
        return createNotPassedNotSkippedSet().stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum();
    }

    private static EnumSet<TestStepResultStatus> createNotPassedNotSkippedSet() {
        EnumSet<TestStepResultStatus> notPassedNotSkipped = EnumSet.allOf(TestStepResultStatus.class);
        notPassedNotSkipped.remove(PASSED);
        notPassedNotSkipped.remove(SKIPPED);
        return notPassedNotSkipped;
    }

    private void writeTestcase(EscapingXmlStreamWriter writer, String id) throws XMLStreamException {
        writer.writeStartElement("testcase");
        writeTestCaseAttributes(writer, id);
        writer.newLine();
        writeNonPassedElement(writer, id);
        writeStepAndResultList(writer, id);
        writer.writeEndElement();
        writer.newLine();
    }

    private void writeTestCaseAttributes(EscapingXmlStreamWriter writer, String id) throws XMLStreamException {
        writer.writeAttribute("classname", data.getFeatureName(id));
        writer.writeAttribute("name", data.getPickleName(id));
        writer.writeAttribute("time", numberFormat.format(data.getDurationInSeconds(id)));
    }

    private void writeNonPassedElement(EscapingXmlStreamWriter writer, String id) throws XMLStreamException {
        TestStepResult result = data.getTestCaseStatus(id);
        TestStepResultStatus status = result.getStatus();
        if (status == TestStepResultStatus.PASSED) {
            return;
        }

        String elementName = status == SKIPPED ? "skipped" : "failure";

        Optional<String> message = result.getMessage();
        Optional<String> exceptionType = result.getException().map(Exception::getType);
        Optional<String> exceptionMessage = result.getException().flatMap(Exception::getMessage);

        if (message.isPresent()) {
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
        if (message.isPresent()) {
            writer.newLine();
            writer.writeCData(message.get());
            writer.newLine();
        }

        if (message.isPresent()) {
            writer.writeEndElement();
        }
        writer.newLine();
    }

    private void writeStepAndResultList(EscapingXmlStreamWriter writer, String id) throws XMLStreamException {
        LinkedHashMap<String, String> results = data.getStepsAndResult(id);
        if (results.isEmpty()) {
            return;
        }
        writer.writeStartElement("system-out");
        writer.writeCData(createStepResultList(results));
        writer.writeEndElement();
        writer.newLine();
    }

    private static String createStepResultList(LinkedHashMap<String, String> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        results.entrySet().forEach(r -> {
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