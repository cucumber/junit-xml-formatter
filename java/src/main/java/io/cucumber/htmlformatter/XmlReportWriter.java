package io.cucumber.htmlformatter;

import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

        writer.writeAttribute("classname", data.getFeatureName(id));
        writer.writeAttribute("name", data.getPickleName(id));
        writer.writeAttribute("time", numberFormat.format(data.getDurationInSeconds(id)));

        writeNonPassedElement(writer, id);

        writer.writeEndElement();
        newLine(writer);
    }

    private void writeNonPassedElement(XMLStreamWriter writer, String id) throws XMLStreamException {
        TestStepResult result = data.getTestCaseStatus(id);
        if (result.getStatus() == TestStepResultStatus.PASSED) {
            return;
        }

        String elementName = result.getStatus() == SKIPPED ? "skipped" : "failure";

        if (result.getMessage().isPresent()) {
            writer.writeStartElement(elementName);
            // writer.writeAttribute("message", ); // TODO: Add to message
            // protocol
            // writer.writeAttribute("type", ); // TODO: Add to message
            // protocol
            newLine(writer);
            // TODO: Write step line listing

            writeCDataSafely(writer, result.getMessage().get());
            writer.writeEndElement();
        } else {
            writer.writeEmptyElement(elementName);
        }
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