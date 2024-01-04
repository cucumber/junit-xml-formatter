package io.cucumber.junitxmlformatter;

import io.cucumber.messages.NdjsonToMessageIterable;
import io.cucumber.messages.types.Envelope;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmlunit.builder.Input;
import org.xmlunit.validation.JAXPValidator;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationProblem;
import org.xmlunit.validation.ValidationResult;

import javax.xml.transform.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.cucumber.junitxmlformatter.Jackson.OBJECT_MAPPER;
import static org.xmlunit.assertj.XmlAssert.assertThat;

class MessagesToJunitXmlWriterAcceptanceTest {
    private static final NdjsonToMessageIterable.Deserializer deserializer = (json) -> OBJECT_MAPPER.readValue(json, Envelope.class);

    static List<TestCase> acceptance() throws IOException {
        try (Stream<Path> paths = Files.list(Paths.get("../testdata"))) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".ndjson"))
                    .map(TestCase::new)
                    .sorted(Comparator.comparing(testCase -> testCase.source))
                    .collect(Collectors.toList());
        }
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void test(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, new ByteArrayOutputStream());
        Source expected = Input.fromPath(testCase.expected).build();
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        assertThat(actual).and(expected).ignoreWhitespace().areIdentical();
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void validateAgainstJenkins(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, new ByteArrayOutputStream());
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        Source jenkinsSchema = Input.fromPath(Paths.get("../jenkins-junit.xsd")).build();
        assertThat(actual).isValidAgainst(jenkinsSchema);
    }

    static final List<String> testCasesWithMissingException = Arrays.asList(
            "examples-tables.feature",
            "hooks.feature",
            "pending.feature",
            "retry.feature",
            "undefined.feature",
            "unknown-parameter-type.feature"
    );

    @ParameterizedTest
    @MethodSource("acceptance")
    void validateAgainstSurefire(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, new ByteArrayOutputStream());
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        Source surefireSchema = Input.fromPath(Paths.get("../surefire-test-report-3.0.xsd")).build();
        if (!testCasesWithMissingException.contains(testCase.name)) {
            assertThat(actual).isValidAgainst(surefireSchema);
            return;
        }

        /*
         This report tries to be compatible with the Jenkins XSD. The Surefire
         XSD is a bit stricter and generally assumes tests fail with an
         exception. While this is true for Cucumber-JVM, this isn't true for
         all Cucumber implementations.

         E.g. in Cucumber-JVM a scenario would report it is pending by throwing
         a PendingException. However, in Javascript this would be done by
         returning the string "pending".

         Since the Surefire XSD is also relatively popular we do check it and
         exclude the cases that don't pass selectively.
         */
        JAXPValidator validator = new JAXPValidator(Languages.W3C_XML_SCHEMA_NS_URI);
        validator.setSchemaSource(surefireSchema);
        ValidationResult validationResult = validator.validateInstance(actual);
        Iterable<ValidationProblem> problems = validationResult.getProblems();
        Assertions.assertThat(problems).extracting(ValidationProblem::getMessage)
                .containsOnly("cvc-complex-type.4: Attribute 'type' must appear on element 'failure'.");
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedXmlReportFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writeJunitXmlReport(testCase, out);
        }
    }

    private static <T extends OutputStream> T writeJunitXmlReport(TestCase testCase, T out) throws IOException {
        try (InputStream in = Files.newInputStream(testCase.source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, deserializer)) {
                try (MessagesToJunitXmlWriter writer = new MessagesToJunitXmlWriter(out)) {
                    for (Envelope envelope : envelopes) {
                        writer.write(envelope);
                    }
                }
            }
        }
        return out;
    }

    static class TestCase {
        private final Path source;
        private final Path expected;

        private final String name;

        TestCase(Path source) {
            this.source = source;
            String fileName = source.getFileName().toString();
            this.name = fileName.substring(0, fileName.lastIndexOf(".ndjson"));
            this.expected = source.getParent().resolve(name + ".xml");
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestCase testCase = (TestCase) o;
            return source.equals(testCase.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source);
        }
    }

}

