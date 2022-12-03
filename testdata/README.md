# Acceptance test data

The junit xml formatter uses some test data for acceptance testing.

Those test data are ndjson files copied from the cucumber compatibility kit.

Having those test data as part of a npm package will enable renovate ot update the
CCK automatically.

The test-testdata github workflow will then help us keeping the test data up-to-date
after the CCK has been updated.
