package io.cucumber.junitxmlformatter.cli;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.cucumber.junitxmlformatter.MessagesToJunitXmlWriter;
import io.cucumber.messages.NdjsonToMessageIterable;
import io.cucumber.messages.NdjsonToMessageIterable.Deserializer;
import io.cucumber.messages.types.Envelope;
import io.cucumber.query.NamingStrategy.ExampleName;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static io.cucumber.junitxmlformatter.cli.JunitXmlFormatter.*;
import static java.nio.file.Files.newOutputStream;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

@Command(
        name = JUNIT_XML_FORMATTER_NAME,
        mixinStandardHelpOptions = true,
        header = "Converts Cucumber messages to JUnit XML",
        versionProvider = ManifestVersionProvider.class
)
class JunitXmlFormatter implements Callable<Integer> {
    static final String JUNIT_XML_FORMATTER_NAME = "junit-xml-formatter";

    @Spec
    private CommandSpec spec;

    @Parameters(
            index = "0",
            paramLabel = "file",
            description = "The input file containing Cucumber messages. " +
                    "Use - to read from the standard input."
    )
    private Path source;

    @Option(
            names = {"-o", "--output"},
            arity = "0..1",
            paramLabel = "file",
            description = "The output file containing JUnit XML. " +
                    "If file is a directory, a new file be " +
                    "created by taking the name of the input file and " +
                    "replacing the suffix with '.xml'. If the file is omitted " +
                    "the current working directory is used."
    )
    private Path output;

    @Option(
            names = {"-e","--example-naming-strategy"},
            paramLabel = "strategy",
            description = "How to name examples. Valid values: ${COMPLETION-CANDIDATES}",
            defaultValue = "NUMBER_AND_PICKLE_IF_PARAMETERIZED"
    )
    private ExampleName exampleNameStrategy;

    @Override
    public Integer call() throws IOException {
        if (isSourceSystemIn()) {
            if (isDestinationDirectory()) {
                throw new ParameterException(
                        spec.commandLine(),
                        ("Invalid value '%s' for option '--output': When " +
                                "reading from standard input, output can not " +
                                "be a directory").formatted(output)
                );
            }
        }

        try (var envelopes = new NdjsonToMessageIterable(sourceInputStream(), deserializer());
             var writer = new MessagesToJunitXmlWriter(exampleNameStrategy, outputPrintWriter())
        ) {
            for (var envelope : envelopes) {
                // TODO: What if exception while writing?
                writer.write(envelope);
            }
        }
        return 0;
    }

    public static void main(String... args) {
        var exitCode = new CommandLine(new JunitXmlFormatter()).execute(args);
        System.exit(exitCode);
    }

    private boolean isSourceSystemIn() {
        return source.getFileName().toString().equals("-");
    }

    private boolean isDestinationDirectory() {
        return output != null && Files.isDirectory(output);
    }

    private static Deserializer deserializer() {
        var jsonMapper = JsonMapper.builder()
                .addModule(new Jdk8Module())
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.USE_LONG_FOR_INTS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return json -> jsonMapper.readValue(json, Envelope.class);
    }

    private PrintWriter outputPrintWriter() {
        if (output == null) {
            return spec.commandLine().getOut();
        }
        Path path = outputPath();
        try {
            return new PrintWriter(
                    new OutputStreamWriter(
                            newOutputStream(path, CREATE, TRUNCATE_EXISTING),
                            StandardCharsets.UTF_8
                    )
            );
        } catch (IOException e) {
            throw new ParameterException(
                    spec.commandLine(),
                    ("Invalid value '%s' for option '--output': Could not " +
                            "write to '%s'"
                    ).formatted(output, path), e);
        }
    }

    private Path outputPath() {
        if (!isDestinationDirectory()) {
            return output;
        }

        // Given a directory, decide on a file name
        var fileName = source.getFileName().toString();
        var index = fileName.lastIndexOf(".");
        if (index >= 0) {
            fileName = fileName.substring(0, index);
        }
        var candidate = output.resolve(fileName + ".xml");

        // Avoid overwriting existing files when we decided the file name.
        var counter = 1;
        while(Files.exists(candidate)) {
            candidate = output.resolve(fileName + "." + counter + ".xml");
        }
        return candidate;
    }

    private InputStream sourceInputStream() {
        if (isSourceSystemIn()) {
            return System.in;
        }
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new ParameterException(spec.commandLine(), "Invalid argument, could not read '%s'".formatted(source), e);
        }
    }


}
