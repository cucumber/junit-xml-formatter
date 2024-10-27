package io.cucumber.junitxmlformatter.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class JunitXmlFormatterTest {

    static final Path minimalFeatureNdjson = Paths.get("../../testdata/minimal.feature.ndjson");
    static final Path minimalFeatureXml = Paths.get("../../testdata/minimal.feature.xml");

    final StringWriter stdOut = new StringWriter();
    final StringWriter stdErr = new StringWriter();
    final CommandLine cmd = new CommandLine(new JunitXmlFormatter());
    InputStream originalSystemIn;

    @TempDir
    Path tmp;

    @BeforeEach
    void setup() {
        originalSystemIn = System.in;
        cmd.setOut(new PrintWriter(stdOut));
        cmd.setErr(new PrintWriter(stdErr));
    }

    @AfterEach
    void cleanup() {
        System.setIn(originalSystemIn);
        // Helps with debugging
        System.out.println(stdOut);
        System.out.println(stdErr);
    }

    @Test
    void help() {
        cmd.printVersionHelp(new PrintWriter(stdOut));
        cmd.execute("--help");
    }

    @Test
    void version() {
        var exitCode = cmd.execute("--version");
        assertAll(
                () -> assertThat(exitCode).isZero(),
                () -> assertThat(stdOut.toString())
                        .isEqualToIgnoringNewLines("junit-xml-formatter DEVELOPMENT")
        );
    }

    @Test
    void writeToSystemOut() {
        var exitCode = cmd.execute("../../testdata/minimal.feature.ndjson");
        assertAll(
                () -> assertThat(exitCode).isZero(),
                () -> assertThat(stdOut.toString())
                        .isEqualTo(readString(minimalFeatureXml))
        );
    }

    @Test
    void failsToReadNonExistingFile() {
        var exitCode = cmd.execute("../../testdata/no-such.feature.ndjson");
        assertAll(
                () -> assertThat(exitCode).isEqualTo(2),
                () -> assertThat(stdErr.toString())
                        .contains("Invalid argument, could not read '../../testdata/no-such.feature.ndjson'")
        );
    }

    @Test
    void readsFromSystemIn() throws IOException {
        System.setIn(newInputStream(minimalFeatureNdjson));
        var exitCode = cmd.execute("-");
        assertAll(
                () -> assertThat(exitCode).isZero(),
                () -> assertThat(stdOut.toString())
                        .isEqualTo(readString(minimalFeatureXml))
        );
    }

    @Test
    void writesToOutputFile() {
        var destination = tmp.resolve("minimal.feature.xml");
        var exitCode = cmd.execute("../../testdata/minimal.feature.ndjson", "--output", destination.toString());
        assertAll(
                () -> assertThat(exitCode).isZero(),
                () -> assertThat(readString(destination))
                        .isEqualTo(readString(minimalFeatureXml))
        );
    }

    @Test
    void doesNotOverwriteWhenWritingToDirectory() {
        var exitCode1 = cmd.execute("../../testdata/minimal.feature.ndjson", "--output", tmp.toString());
        var exitCode2 = cmd.execute("../../testdata/minimal.feature.ndjson", "--output", tmp.toString());
        assertAll(
                () -> assertThat(exitCode1).isZero(),
                () -> assertThat(tmp.resolve("minimal.feature.xml")).exists(),
                () -> assertThat(exitCode2).isZero(),
                () -> assertThat(tmp.resolve("minimal.feature.1.xml")).exists()
        );
    }

    @Test
    void failsToWriteToForbiddenOutputFile() throws IOException {
        var destination = Files.createFile(tmp.resolve("minimal.feature.xml"));
        var isReadOnly = destination.toFile().setReadOnly();
        assertThat(isReadOnly).isTrue();

        var exitCode = cmd.execute("../../testdata/minimal.feature.ndjson", "--output", destination.toString());
        assertAll(
                () -> assertThat(exitCode).isEqualTo(2),
                () -> assertThat(stdErr.toString())
                        .contains("Invalid value '%s' for option '--output': Could not write to '%s'"
                                .formatted(destination, destination))
        );
    }

    @Test
    void writesFileToCurrentWorkingDirectory() {
        var destination = Paths.get("minimal.feature.xml");
        var exitCode = cmd.execute("../../testdata/minimal.feature.ndjson", "--output");
        assertAll(
                () -> assertThat(exitCode).isZero(),
                () -> assertThat(readString(destination))
                        .isEqualTo(readString(minimalFeatureXml))
        );
    }

    @Test
    void canNotGuessFileNameWhenReadingFromSystemIn() throws IOException {
        System.setIn(newInputStream(minimalFeatureNdjson));
        var exitCode = cmd.execute("-", "--output");
        assertAll(
                () -> assertThat(exitCode).isEqualTo(2),
                () -> assertThat(stdErr.toString())
                        .contains("Invalid value '' for option '--output': When reading from standard input, output can not be a directory")
        );
    }

    @Test
    void useExampleNamingStrategy() {
        var exitCode = cmd.execute("../../testdata/examples-tables.feature.ndjson", "--example-naming-strategy", "NUMBER");
        assertAll(
                () -> assertThat(exitCode).isZero(),
                () -> assertThat(stdOut.toString())
                        .contains("name=\"Eating cucumbers with &lt;friends&gt; friends - #1.1\"")
        );
    }

}
