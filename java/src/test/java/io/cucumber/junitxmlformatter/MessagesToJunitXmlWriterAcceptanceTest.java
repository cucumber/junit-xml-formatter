package io.cucumber.junitxmlformatter;

import io.cucumber.compatibilitykit.MessageOrderer;
import io.cucumber.messages.NdjsonToMessageReader;
import io.cucumber.messages.ndjson.Deserializer;
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

import static io.cucumber.query.NamingStrategy.Strategy.LONG;
import static io.cucumber.query.NamingStrategy.strategy;
import static java.util.Objects.requireNonNull;
import static org.xmlunit.assertj.XmlAssert.assertThat;

class MessagesToJunitXmlWriterAcceptanceTest {
    private static final Random random = new Random(202509282040L);
    private static final MessageOrderer messageOrderer = new MessageOrderer(random);

    static List<TestCase> acceptance() throws IOException {
        List<TestCase> testCases = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get("../testdata/src"))) {
            paths
                    .filter(path -> path.getFileName().toString().endsWith(".ndjson"))
                    .map(source -> new TestCase(
                                    source,
                                    "default",
                                    MessagesToJunitXmlWriter.builder()
                            )
                    )
                    .sorted(Comparator.comparing(testCase -> testCase.source))
                    .forEach(testCases::add);
        }

        testCases.add(
                new TestCase(
                        Paths.get("../testdata/src/examples-tables.ndjson"),
                        "custom",
                        MessagesToJunitXmlWriter.builder()
                                .testSuiteName("Cucumber Suite")
                                .testClassName("Cucumber Class")
                                .testNamingStrategy(strategy(LONG).build())
                )
        );

        return testCases;
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
        ByteArrayOutputStream bytes = writeJunitXmlReport(testCase, messageOrderer.simulateParallelExecution());
        Source expected = Input.fromPath(testCase.expected).build();
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
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
        try (var in = Files.newInputStream(testCase.source)) {
            try (var reader = new NdjsonToMessageReader(in, new Deserializer())) {
                try (MessagesToJunitXmlWriter writer = testCase.getBuilder().build(out)) {
                    List<Envelope> messages = reader.lines().collect(Collectors.toList());
                    orderer.accept(messages);
                    for (Envelope envelope : messages) {
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
        private final MessagesToJunitXmlWriter.Builder builder;
        private final String strategyName;

        TestCase(Path source, String namingStrategyName, MessagesToJunitXmlWriter.Builder builder) {
            this.source = source;
            String fileName = source.getFileName().toString();
            this.name = fileName.substring(0, fileName.lastIndexOf(".ndjson"));
            this.expected = requireNonNull(source.getParent()).resolve(name + "." + namingStrategyName + ".xml");
            this.builder = builder;
            this.strategyName = namingStrategyName;
        }

        MessagesToJunitXmlWriter.Builder getBuilder() {
            return builder;
        }

        @Override
        public String toString() {
            return name + " -> " + strategyName;
        }

    }

}

