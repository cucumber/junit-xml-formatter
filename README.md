[![Maven Central](https://img.shields.io/maven-central/v/io.cucumber/junit-xml-formatter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.cucumber%22%20AND%20a:%22junit-xml-formatter%22)

⚠️ This is an internal package; you don't need to install it in order to use the JUnit XML Formatter.

JUnit XML Formatter
===================

Writes Cucumber message into a JUnit XML report. 

The JUnit XML report is a loose standard. We validate it against the 
[Jenkins JUnit XML XSD](./jenkins-junit.xsd) so there should be a good
chance your CI will understand it.

If not, please let us know in the issues!