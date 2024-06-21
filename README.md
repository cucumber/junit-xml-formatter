[![Maven Central](https://img.shields.io/maven-central/v/io.cucumber/junit-xml-formatter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.cucumber%20AND%20a:junit-xml-formatter)

⚠️ This is an internal package; you don't need to install it in order to use the JUnit XML Formatter.

JUnit XML Formatter
===================

Writes Cucumber message into a JUnit XML report.

The JUnit XML report is a loose standard. We validate it against the
[Jenkins JUnit XML XSD](./jenkins-junit.xsd) so there should be a good
chance your CI will understand it.

If not, please let us know in the issues!

## Features and Limitations

### Test outcome mapping

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


### Step reporting

The JUnit XML report assumes that a test is a method on a class. Yet a scenario
consists of multiple steps. To provide info about which step failed, the `system-out`
element will contain a rendition of steps and their result.

```xml
<system-out><![CDATA[
Given there are 12 cucumbers................................................passed
When I eat 5 cucumbers......................................................passed
Then I should have 7 cucumbers..............................................passed
]]></system-out>
```

### Naming Rules and Examples

Cucumber does not require that scenario names are unique. To disambiguate
between similarly named scenarios and examples the report prefixes the rule
to the scenario or example name.

```feature
Feature: Rules

  Rule: a sale cannot happen if change cannot be returned
    Example: no change
      ...
    Example: exact change
      ...

  Rule: a sale cannot happen if we're out of stock
    Example: no chocolates left
      ...
```

```xml
<testcase classname="Rules" name="a sale cannot happen if change cannot be returned - no change" time="0.007" />
<testcase classname="Rules" name="a sale cannot happen if change cannot be returned - exact change" time="0.009" />
<testcase classname="Rules" name="a sale cannot happen if we're out of stock - no chocolates left" time="0.009" />
```

Likewise for example tables, the rule (if any), scenario outline name, example
name, and number are included. Additionally, if the scenario outline name is
parameterized, the pickle name is included too.

```feature
Feature: Examples Tables

  Scenario Outline: Eating cucumbers
    Given there are <start> cucumbers
    When I eat <eat> cucumbers
    Then I should have <left> cucumbers

    Examples: These are passing
      | start | eat | left |
      |    12 |   5 |    7 |
      |    20 |   5 |   15 |

    Examples: These are failing
      | start | eat | left |
      |    12 |  20 |    0 |
      |     0 |   1 |    0 |

  Scenario Outline: Eating <color> cucumbers
    Given I am transparent
    When I eat <color> cucumbers
    Then I become <color>

    Examples:
      | color | 
      |   red | 
      | green | 
      |  blue | 
```

```xml
<testcase classname="Examples Tables" name="Eating cucumbers - These are passing - #1.1" />
<testcase classname="Examples Tables" name="Eating cucumbers - These are passing - #1.2" />
<testcase classname="Examples Tables" name="Eating cucumbers - These are failing - #2.1" />
<testcase classname="Examples Tables" name="Eating cucumbers - These are failing - #2.2" />
<testcase classname="Examples Tables" name="Eating &lt;color&gt; cucumbers - #1.1: Eating red cucumbers" />
<testcase classname="Examples Tables" name="Eating &lt;color&gt; cucumbers - #1.2: Eating green cucumbers" />
<testcase classname="Examples Tables" name="Eating &lt;color&gt; cucumbers - #1.3: Eating blue cucumbers" />
```
## Contributing

Each language implementation validates itself against the examples in the
`testdata` folder. See the [testdata/README.md](testdata/README.md) for more
information.