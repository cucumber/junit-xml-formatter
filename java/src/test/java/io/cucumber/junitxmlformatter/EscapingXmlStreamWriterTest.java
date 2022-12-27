package io.cucumber.junitxmlformatter;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class EscapingXmlStreamWriterTest {

    @Test
    void shouldWriteDocument() throws XMLStreamException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (EscapingXmlStreamWriter writer = createWriter(out)){
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeEmptyElement("test");
            writer.writeAttribute("key", "Hello world");
            writer.writeEndDocument();
        }
        assertThat(asString(out))
                .isEqualTo("<test key=\"Hello world\"/>");
    }

    @Test
    void shouldEscapeQuotesAndBracketsInAttribute() throws XMLStreamException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (EscapingXmlStreamWriter writer = createWriter(out)){
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeEmptyElement("test");
            writer.writeAttribute("key", "<\"Hello world\">");
            writer.writeEndDocument();
        }
        assertThat(asString(out))
                .isEqualTo("<test key=\"&lt;&quot;Hello world&quot;&gt;\"/>");
    }

    @Test
    void shouldEscapeNullInAttribute() throws XMLStreamException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (EscapingXmlStreamWriter writer = createWriter(out)){
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeEmptyElement("test");
            writer.writeAttribute("key", "Hello \0 world");
            writer.writeEndDocument();
        }
        assertThat(asString(out))
                .isEqualTo("<test key=\"Hello &amp;#0; world\"/>");
    }

    @Test
    void shouldEscapeCDataElement() throws XMLStreamException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (EscapingXmlStreamWriter writer = createWriter(out)){
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCData("Hello <![CDATA[ cdata ]]> world");
            writer.writeEndDocument();
        }
        assertThat(asString(out))
                .isEqualTo("<![CDATA[Hello <![CDATA[ cdata ]]]]><![CDATA[> world]]>");
    }

    private static String asString(ByteArrayOutputStream out) {
        String s = new String(out.toByteArray(), UTF_8);
        return removeXmlHeader(s);
    }

    private static String removeXmlHeader(String s) {
        String prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        if (s.startsWith(prefix)) {
            return s.substring(prefix.length());
        }
        return s;
    }

    private static EscapingXmlStreamWriter createWriter(ByteArrayOutputStream out) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlStreamWriter = factory.createXMLStreamWriter(out);
        return new EscapingXmlStreamWriter(xmlStreamWriter);
    }

}