# frozen_string_literal: true

RSpec.describe Cucumber::JunitXmlFormatter do
  it 'exposes the package version' do
    expect(described_class::VERSION).to eq('0.13.3')
  end
end
