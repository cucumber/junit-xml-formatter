package io.cucumber.junitxmlformatter;

import io.cucumber.compatibilitykit.MessageOrderer;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.cucumber.junitxmlformatter.Jackson.OBJECT_MAPPER;
import static org.xmlunit.assertj.XmlAssert.assertThat;

class MessagesToJunitXmlWriterAcceptanceTest {
    private static final NdjsonToMessageIterable.Deserializer deserializer = (json) -> OBJECT_MAPPER.readValue(json, Envelope.class);
    private static final Random random = new Random(202509282040L);
    private static final MessageOrderer messageOrderer = new MessageOrderer(random);

    static List<TestCase> acceptance() throws IOException {
        try (Stream<Path> paths = Files.list(Paths.get("../testdata/src"))) {
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
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, messageOrderer.originalOrder());
        Source expected = Input.fromPath(testCase.expected).build();
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        assertThat(actual).and(expected).ignoreWhitespace().areIdentical();
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void testWithSimulatedParallelExecution(TestCase testCase) throws IOException {
        ByteArrayOutputStream actual = writeJunitXmlReport(testCase, messageOrderer.simulateParallelExecution());
        byte[] expected = Files.readAllBytes(testCase.expected);
        assertThat(actual).and(expected).ignoreWhitespace().areIdentical();
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void validateAgainstJenkins(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, messageOrderer.originalOrder());
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        Source jenkinsSchema = Input.fromPath(Paths.get("../jenkins-junit.xsd")).build();
        assertThat(actual).isValidAgainst(jenkinsSchema);
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void validateAgainstSurefire(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, messageOrderer.originalOrder());
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        Source surefireSchema = Input.fromPath(Paths.get("../surefire-test-report-3.0.2.xsd")).build();

        JAXPValidator validator = new JAXPValidator(Languages.W3C_XML_SCHEMA_NS_URI);
        validator.setSchemaSource(surefireSchema);
        ValidationResult validationResult = validator.validateInstance(actual);

        List<String> expectedProblems = new ArrayList<>();
        /*
         * We add the timestamp attribute to all reports.
         */
        expectedProblems.add("cvc-complex-type.3.2.2: Attribute 'timestamp' is not allowed to appear in element 'testsuite'.");

        Iterable<ValidationProblem> problems = validationResult.getProblems();
        Assertions.assertThat(problems).extracting(ValidationProblem::getMessage).containsAll(expectedProblems);
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writeJunitXmlReport(testCase, out, messageOrderer.originalOrder());
        }
    }

    private static ByteArrayOutputStream writeJunitXmlReport(TestCase testCase, Consumer<List<Envelope>> orderer) throws IOException {
        return writeJunitXmlReport(testCase, new ByteArrayOutputStream(), orderer);
    }
    private static <T extends OutputStream> T writeJunitXmlReport(TestCase testCase, T out, Consumer<List<Envelope>> orderer) throws IOException {
        List<Envelope> messages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(testCase.source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, deserializer)) {
                for (Envelope envelope : envelopes) {
                    messages.add(envelope);
                }
            }
        }
        orderer.accept(messages);

        try (MessagesToJunitXmlWriter writer = new MessagesToJunitXmlWriter(out)) {
            for (Envelope envelope : messages) {
                writer.write(envelope);
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

    }

}

