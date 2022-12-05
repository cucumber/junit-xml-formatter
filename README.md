[![Maven Central](https://img.shields.io/maven-central/v/io.cucumber/junit-xml-formatter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.cucumber%22%20AND%20a:%22junit-xml-formatter%22)

⚠️ This is an internal package; you don't need to install it in order to use the JUnit XML Formatter.

JUnit XML Formatter
===================

Writes Cucumber message into a JUnit XML report.

The JUnit XML report is a loose standard. We validate it against the
[Jenkins JUnit XML XSD](./jenkins-junit.xsd) so there should be a good
chance your CI will understand it.

If not, please let us know in the issues!

## Limitations

Cucumber and the JUnit XML Report support a different set of test outcomes.
These are mapped according to the table below. 

Additionally, it is advisable to run Cucumber in strict mode. When used in
non-strict mode scenarios with a pending or undefined outcome will not fail
the test run ([#714](https://github.com/cucumber/common/issues/714)). This
can lead to a xml report that contains `failure` outcomes while the build
passes.

| Cucumber Outcome | XML Outcome | Passes in strict mode | Passes in non-strict mode |
|------------------|-------------|-----------------------|---------------------------|
| UNKNOWN          | n/a         | n/a                   | n/a                       |
| PASSED           | passed      | yes                   | yes                       |            
| SKIPPED          | skipped     | yes                   | yes                       |           
| PENDING          | failure     | no                    | yes                       |
| UNDEFINED        | failure     | no                    | yes                       |
| AMBIGUOUS        | failure     | no                    | no                        |
| FAILED           | failure     | no                    | no                        |


