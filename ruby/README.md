# Cucumber JUnit XML Formatter for Ruby

`cucumber-junit-xml-formatter` is the Ruby implementation of the polyglot
Cucumber JUnit XML formatter. It will consume `Cucumber::Messages::Envelope`
objects and produce a JUnit XML report compatible with the shared formatter
fixtures in this repository.

The public API is `Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter`.
`Cucumber::JunitXmlFormatter::JUnitXmlPrinter` is exported as an alias for
parity with the JavaScript package. Full message-to-XML report semantics are
being implemented incrementally against the shared fixtures.

## Installation

```ruby
gem 'cucumber-junit-xml-formatter', '~> 0.13'
```

Ruby 3.2 or newer is required. The gem depends on `cucumber-messages` and
`cucumber-query`.

## Usage

```ruby
require 'cucumber/junit_xml_formatter'

writer = Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter.new(
  stream: output_io,
  suite_name: 'Cucumber',
  test_class_name: nil,
  test_naming_strategy: nil
)

envelopes.each { |envelope| writer.write(envelope) }
writer.close
```

`#update` is an alias for `#write`, matching the message-driven printer idiom in
other Cucumber formatter packages. The writer finalizes automatically when it
receives a `testRunFinished` envelope; calling `#close` is safe and idempotent.

Supported constructor options are:

- `stream:` - required IO-like destination that responds to `#write`.
- `suite_name:` - optional JUnit `<testsuite name>` value, defaulting to
  `"Cucumber"`.
- `test_class_name:` - optional classname override for generated test cases.
- `test_naming_strategy:` - optional naming strategy hook for the report model.

The standalone library intentionally does not model cucumber-ruby-specific
concerns such as per-feature output files or the legacy `fileattribute` option.

## Local development dependencies

Use released gems by default once the Ruby `cucumber-query` gem is available.
While developing against local checkouts, set environment variables before
running Bundler:

```sh
cd repos/junit-xml-formatter/ruby
CUCUMBER_MESSAGES_RUBY_PATH=../../messages/ruby \
CUCUMBER_QUERY_RUBY_PATH=../../query/ruby \
bundle install
```

You can also test against an unreleased query branch/ref:

```sh
CUCUMBER_QUERY_RUBY_REF=ruby-implementation bundle install
```

Run the Ruby checks from this directory:

```sh
bundle exec rake
```
