package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.TestRunFinished;
import io.cucumber.messages.types.TestRunStarted;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static io.cucumber.messages.Convertor.toMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesToJunitXmlWriterTest {

    @Test
    void it_writes_two_messages_to_xml() throws IOException {
        Instant started = Instant.ofEpochSecond(10);
        Instant finished = Instant.ofEpochSecond(30);

        String html = renderAsJunitXml(
                Envelope.of(new TestRunStarted(toMessage(started), "some-id")),
                Envelope.of(new TestRunFinished(null, true, toMessage(finished), null, "some-id")));

        assertThat(html).isEqualTo("" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<testsuite name=\"Cucumber\" time=\"20\" tests=\"0\" skipped=\"0\" failures=\"0\" errors=\"0\" timestamp=\"1970-01-01T00:00:10Z\">\n" +
                "</testsuite>\n"
        );
    }

    @Test
    void it_writes_no_message_to_xml() throws IOException {
        String html = renderAsJunitXml();
        assertThat(html).isEqualTo("" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<testsuite name=\"Cucumber\" time=\"0\" tests=\"0\" skipped=\"0\" failures=\"0\" errors=\"0\">\n" +
                "</testsuite>\n"
        );
    }

    @Test
    void it_throws_when_writing_after_close() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessagesToJunitXmlWriter messagesToHtmlWriter = new MessagesToJunitXmlWriter(bytes);
        messagesToHtmlWriter.close();
        assertThrows(IOException.class, () -> messagesToHtmlWriter.write(null));
    }

    @Test
    void it_can_be_closed_twice() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessagesToJunitXmlWriter messagesToHtmlWriter = new MessagesToJunitXmlWriter(bytes);
        messagesToHtmlWriter.close();
        assertDoesNotThrow(messagesToHtmlWriter::close);
    }

    @Test
    void it_is_idempotent_under_failure_to_close() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                throw new IOException("Can't close this");
            }
        };
        MessagesToJunitXmlWriter messagesToHtmlWriter = new MessagesToJunitXmlWriter(bytes);
        assertThrows(IOException.class, messagesToHtmlWriter::close);
        byte[] before = bytes.toByteArray();
        assertDoesNotThrow(messagesToHtmlWriter::close);
        byte[] after = bytes.toByteArray();
        assertThat(after).isEqualTo(before);
    }


    private static String renderAsJunitXml(Envelope... messages) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MessagesToJunitXmlWriter messagesToHtmlWriter = new MessagesToJunitXmlWriter(bytes)) {
            for (Envelope message : messages) {
                messagesToHtmlWriter.write(message);
            }
        }

        return new String(bytes.toByteArray(), UTF_8);
    }
}
