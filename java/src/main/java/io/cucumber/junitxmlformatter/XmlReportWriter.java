package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Exception;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static io.cucumber.messages.types.TestStepResultStatus.SKIPPED;
import static java.util.Locale.ROOT;

class XmlReportWriter {
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    private final XmlReportData data;

    XmlReportWriter(XmlReportData data) {
        this.data = data;
    }

    void writeXmlReport(Writer out) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(out);
        writer.writeStartDocument("UTF-8", "1.0");
        newLine(writer);
        writeTestsuite(data, writer);
        writer.writeEndDocument();
        writer.flush();
    }

    private void writeTestsuite(XmlReportData data, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("testsuite");
        writeSuiteAttributes(writer);
        newLine(writer);

        for (String testCaseStartedId : data.testCaseStartedIds()) {
            writeTestcase(writer, testCaseStartedId);
        }

        writer.writeEndElement();
        newLine(writer);
    }

    private void writeSuiteAttributes(XMLStreamWriter writer) throws XMLStreamException {
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

    private void writeTestcase(XMLStreamWriter writer, String id) throws XMLStreamException {
        writer.writeStartElement("testcase");
        writeTestCaseAttributes(writer, id);
        newLine(writer);
        writeNonPassedElement(writer, id);
        writeStepAndResultList(writer, id);
        writer.writeEndElement();
        newLine(writer);
    }

    private void writeTestCaseAttributes(XMLStreamWriter writer, String id) throws XMLStreamException {
        writer.writeAttribute("classname", data.getFeatureName(id));
        writer.writeAttribute("name", data.getPickleName(id));
        writer.writeAttribute("time", numberFormat.format(data.getDurationInSeconds(id)));
    }

    private void writeNonPassedElement(XMLStreamWriter writer, String id) throws XMLStreamException {
        TestStepResult result = data.getTestCaseStatus(id);
        if (result.getStatus() == TestStepResultStatus.PASSED) {
            return;
        }

        String elementName = result.getStatus() == SKIPPED ? "skipped" : "failure";

        Optional<String> message = result.getMessage();
        Optional<String> exceptionType = result.getException().map(Exception::getType);
        Optional<String> exceptionMessage = result.getException().flatMap(Exception::getMessage);

        if (!(message.isPresent() || exceptionType.isPresent() || exceptionMessage.isPresent())) {
            writer.writeEmptyElement(elementName);
            newLine(writer);
            return;
        }

        writer.writeStartElement(elementName);
        if (exceptionType.isPresent()) {
            writer.writeAttribute("type", exceptionType.get());
        }
        if (exceptionMessage.isPresent()) {
            writer.writeAttribute("message", exceptionMessage.get());
        }
        if (message.isPresent()) {
            newLine(writer);
            writeCDataSafely(writer, message.get());
            newLine(writer);
        }
        writer.writeEndElement();
        newLine(writer);
    }

    private void writeStepAndResultList(XMLStreamWriter writer, String id) throws XMLStreamException {
        LinkedHashMap<String, String> results = data.getStepsAndResult(id);
        if (results.isEmpty()) {
            return;
        }

        writer.writeStartElement("system-out");

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
        writeCDataSafely(writer, sb.toString());
        writer.writeEndElement();
        newLine(writer);
    }

    private static final Pattern CDATA_TERMINATOR_SPLIT = Pattern.compile("(?<=]])(?=>)");

    private static void writeCDataSafely(XMLStreamWriter writer, String data) throws XMLStreamException {
        // https://stackoverflow.com/questions/223652/is-there-a-way-to-escape-a-cdata-end-token-in-xml
        for (String part : CDATA_TERMINATOR_SPLIT.split(data)) {
            writer.writeCData(part);
        }
    }

    private void newLine(XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeCharacters("\n");
    }
}