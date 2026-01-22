module io.cucumber.junitxmlformatter {
    requires org.jspecify;

    requires java.xml;

    requires transitive io.cucumber.messages;
    requires io.cucumber.query;

    exports io.cucumber.junitxmlformatter;
}
