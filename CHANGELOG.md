# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.0] - 2025-09-11
### Changed
- Update dependency cucumber/query  to 14.0.1
- Update dependency io.cucumber:query up to v14.0.1

### Fixed
- Remove unnecessary coupling to `node:assert` ([#77](https://github.com/cucumber/junit-xml-formatter/pull/77))

## [0.8.1] - 2025-08-13
### Fixed
- [Java] Format time as xs:float ([#83](https://github.com/cucumber/junit-xml-formatter/pull/83))

## [0.8.0] - 2025-07-07
### Added
- Update dependency io.cucumber:messages up to v28

## [0.7.1] - 2024-11-24
### Fixed
- Correct TypeScript output config ([#56](https://github.com/cucumber/junit-xml-formatter/pull/56))

## [0.7.0] - 2024-11-24
### Added
- Add `timestamp` attribute ((#45)[https://github.com/cucumber/junit-xml-formatter/pull/45])

## [0.6.0] - 2024-11-15
### Added
- JavaScript implementation ([#42](https://github.com/cucumber/junit-xml-formatter/pull/42))
- Update dependency io.cucumber:messages up to v26 ([#38](https://github.com/cucumber/junit-xml-formatter/pull/38))

### Fixed
- Attempts that have been superseded by retries are now omitted from the report ([#42](https://github.com/cucumber/junit-xml-formatter/pull/42))

## [0.5.0] - 2024-06-22
### Added
- Include pickle name if parameterized [#34](https://github.com/cucumber/junit-xml-formatter/pull/34)
- Support configurable `NamingStrategy.ExampleName`  ([#32](https://github.com/cucumber/cucumber-junit-xml-formatter/pull/32), M.P. Korstanje)

## [0.4.0] - 2024-04-05
### Changed
- Extracted common code to [Cucumber Query](https://github.com/cucumber/query/tree/main) ([#31](https://github.com/cucumber/cucumber-junit-xml-formatter/pull/31), M.P. Korstanje)

## [0.3.0] - 2024-03-23
### Added
- Include value from Exception.stacktrace if available ([#30](https://github.com/cucumber/junit-xml-formatter/pull/30), M.P. Korstanje)

### Fixed
- Do not overwrite results of retried tests ([#29](https://github.com/cucumber/junit-xml-formatter/pull/29), M.P. Korstanje)

## [0.2.1] - 2024-02-15
### Fixed
- Missing execution steps statuses if same step is called multiple times in a test ([#24](https://github.com/cucumber/junit-xml-formatter/pull/24) F. Ahadi)

## [0.2.0] - 2023-04-07

## [0.1.0] - 2022-12-27
### Added
- Java implementation ([#3](https://github.com/cucumber/junit-xml-formatter/pull/3) M.P. Korstanje)

[Unreleased]: https://github.com/cucumber/junit-xml-formatter/compare/v0.9.0...HEAD
[0.9.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.8.1...v0.9.0
[0.8.1]: https://github.com/cucumber/junit-xml-formatter/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/cucumber/junit-xml-formatter/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/cucumber/junit-xml-formatter/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/cucumber/junit-xml-formatter/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/cucumber/junit-xml-formatter/compare/438ec1f6218a849eb2a684982e2ff7e304a3155f...v0.1.0
