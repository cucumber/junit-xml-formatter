package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.query.NamingStrategy;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import static io.cucumber.query.NamingStrategy.FeatureName.EXCLUDE;
import static io.cucumber.query.NamingStrategy.Strategy.LONG;
import static java.util.Objects.requireNonNull;

/**
 * Writes the message output of a test run as single page xml report.
 * <p>
 * Note: Messages are first collected and only written once the stream is closed.
 *
 * @see <a href=https://github.com/cucumber/junit-xml-formatter>Cucumber JUnit XML Formatter - README.md</a>
 */
public class MessagesToJunitXmlWriter implements AutoCloseable {

    private final OutputStreamWriter out;
    private final XmlReportData data;
    private boolean streamClosed = false;

    public MessagesToJunitXmlWriter(OutputStream out) {
        this(NamingStrategy.ExampleName.NUMBER_AND_PICKLE_IF_PARAMETERIZED, out);
    }

    public MessagesToJunitXmlWriter(NamingStrategy.ExampleName exampleNameStrategy, OutputStream out) {
        this(createNamingStrategy(requireNonNull(exampleNameStrategy)), out);
    }

    private static NamingStrategy createNamingStrategy(NamingStrategy.ExampleName exampleName) {
        return NamingStrategy.strategy(LONG).featureName(EXCLUDE).exampleName(exampleName).build();
    }

    private MessagesToJunitXmlWriter(NamingStrategy namingStrategy, OutputStream out) {
        this.data = new XmlReportData(namingStrategy);
        this.out = new OutputStreamWriter(
                requireNonNull(out),
                StandardCharsets.UTF_8
        );
    }

    /**
     * Writes a cucumber message to the xml output.
     *
     * @param envelope the message
     * @throws IOException if an IO error occurs
     */
    public void write(Envelope envelope) throws IOException {
        if (streamClosed) {
            throw new IOException("Stream closed");
        }
        data.collect(envelope);
    }

    /**
     * Closes the stream, flushing it first. Once closed further write()
     * invocations will cause an IOException to be thrown. Closing a closed
     * stream has no effect.
     *
     * @throws IOException if an IO error occurs
     */
    @Override
    public void close() throws IOException {
        if (streamClosed) {
            return;
        }

        try {
            new XmlReportWriter(data).writeXmlReport(out);
        } catch (XMLStreamException e) {
            throw new IOException("Error while transforming.", e);
        } finally {
            try {
                out.close();
            } finally {
                streamClosed = true;
            }
        }
    }
}
