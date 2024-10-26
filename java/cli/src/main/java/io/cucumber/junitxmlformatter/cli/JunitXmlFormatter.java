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
import io.cucumber.query.NamingStrategy;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static io.cucumber.query.NamingStrategy.ExampleName.NUMBER_AND_PICKLE_IF_PARAMETERIZED;

// TODO: Read version from manifest?
@Command(
        name = "junit-xml-formatter",
        mixinStandardHelpOptions = true,
        description = "Converts Cucumber messages to JUnit XML"
)
class JunitXmlFormatter implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "The source file containing Cucumber messages")
    private Path source;

    @Override
    public Integer call() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModule(new Jdk8Module())
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.USE_LONG_FOR_INTS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Deserializer deserializer = json -> jsonMapper.readValue(json, Envelope.class);

        // TODO: Use the CLI options.
        NamingStrategy.ExampleName exampleNameStrategy = NUMBER_AND_PICKLE_IF_PARAMETERIZED;
        // TODO: Read from standard in, or read/write from files.
        Writer out = spec.commandLine().getOut();

        try (InputStream in = Files.newInputStream(source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, deserializer)) {
                try (MessagesToJunitXmlWriter writer = new MessagesToJunitXmlWriter(exampleNameStrategy, out)) {
                    for (Envelope envelope : envelopes) {
                        // TODO: What if exception?
                        writer.write(envelope);
                    }
                }
            }
        }
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new JunitXmlFormatter()).execute(args);
        System.exit(exitCode);
    }
}
