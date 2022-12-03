[![Maven Central](https://img.shields.io/maven-central/v/io.cucumber/junit-xml-formatter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.cucumber%22%20AND%20a:%22junit-xml-formatter%22)

⚠️ This is an internal package; you don't need to install it in order to use the html formatter.

JUnit XML Formatter
===================

Writes Cucumber message into a JUnit XML report. 

The JUnit XML report is a loose standard. We validate it against:

 * [Jenkins JUnit XML XSD](./jenkins-junit.xsd)
 * [Surefire Test Report 3.0 XSD](./surefire-test-report-3.0.xsd)

so there should be a good chance your CI will understand it.