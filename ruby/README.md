# Cucumber JUnit XML Formatter for Ruby

`cucumber-junit-xml-formatter` is the Ruby implementation of the polyglot
Cucumber JUnit XML formatter. It will consume `Cucumber::Messages::Envelope`
objects and produce a JUnit XML report compatible with the shared formatter
fixtures in this repository.

This package is currently a skeleton for the Ruby implementation. The public
writer/printer API and full message-to-XML behaviour will be added in follow-up
work.

## Installation

```ruby
gem 'cucumber-junit-xml-formatter', '~> 0.13'
```

Ruby 3.2 or newer is required. The gem depends on `cucumber-messages` and
`cucumber-query`.

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
