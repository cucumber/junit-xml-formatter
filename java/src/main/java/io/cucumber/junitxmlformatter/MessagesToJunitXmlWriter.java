package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.query.NamingStrategy;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import static io.cucumber.query.NamingStrategy.ExampleName.NUMBER_AND_PICKLE_IF_PARAMETERIZED;
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
public final class MessagesToJunitXmlWriter implements AutoCloseable {

    private static final String DEFAULT_TEST_SUITE_NAME = "Cucumber";
    private final OutputStreamWriter out;
    private final XmlReportData data;
    private boolean streamClosed = false;

    public MessagesToJunitXmlWriter(OutputStream out) {
        this("Cucumber", null, createNamingStrategy(NUMBER_AND_PICKLE_IF_PARAMETERIZED), out);
    }

    @Deprecated
    public MessagesToJunitXmlWriter(NamingStrategy.ExampleName exampleNameStrategy, OutputStream out) {
        this("Cucumber", null, createNamingStrategy(requireNonNull(exampleNameStrategy)), out);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static NamingStrategy createNamingStrategy(NamingStrategy.ExampleName exampleName) {
        return NamingStrategy.strategy(NamingStrategy.Strategy.LONG).featureName(NamingStrategy.FeatureName.EXCLUDE).exampleName(exampleName).build();
    }

    private MessagesToJunitXmlWriter(String testSuiteName, @Nullable String testClassName, NamingStrategy testNamingStrategy, OutputStream out) {
        this.data = new XmlReportData(testSuiteName, testClassName, testNamingStrategy);
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

    public final static class Builder {

        private String testSuiteName = DEFAULT_TEST_SUITE_NAME;
        private @Nullable String testClassName;
        private NamingStrategy testNamingStrategy = NamingStrategy.strategy(LONG)
                .featureName(EXCLUDE)
                .exampleName(NUMBER_AND_PICKLE_IF_PARAMETERIZED)
                .build();

        private Builder() {

        }

        /**
         * Sets the value for the {@code <testsuite name="..." .../>} attribute. Defaults to {@value DEFAULT_TEST_SUITE_NAME}.
         */
        public Builder testSuiteName(String testSuiteName) {
            this.testSuiteName = requireNonNull(testSuiteName);
            return this;
        }

        /**
         * Sets the value for the {@code <testcase classname="..." .../>} attribute. Defaults to the name of the
         * feature.
         */
        public Builder testClassName(@Nullable String testClassName) {
            this.testClassName = testClassName;
            return this;
        }

        /**
         * Set the naming strategy used for the {@code <testcase name="...".../> attribute}. Defaults to the
         * {@link NamingStrategy.Strategy#LONG} strategy with {@link NamingStrategy.FeatureName#EXCLUDE} and
         * {@link NamingStrategy.ExampleName#NUMBER_AND_PICKLE_IF_PARAMETERIZED}.
         */
        public Builder testNamingStrategy(NamingStrategy namingStrategy) {
            this.testNamingStrategy = requireNonNull(namingStrategy);
            return this;
        }

        public MessagesToJunitXmlWriter build(OutputStream out) {
            return new MessagesToJunitXmlWriter(testSuiteName, testClassName, testNamingStrategy, requireNonNull(out));
        }
    }
}
