# frozen_string_literal: true

require 'stringio'

RSpec.describe Cucumber::JunitXmlFormatter do
  it 'exposes the package version' do
    expect(described_class::VERSION).to eq('0.13.3')
  end

  describe Cucumber::JunitXmlFormatter::MessagesToJunitXmlWriter do
    let(:stream) { StringIO.new }

    it 'is the public printer API exported by the package' do
      expect(Cucumber::JunitXmlFormatter::JUnitXmlPrinter).to be(described_class)
    end

    it 'accepts suite naming options' do
      writer = described_class.new(stream:, suite_name: 'Cucumber Suite', test_class_name: 'Cucumber Class')

      expect(writer.suite_name).to eq('Cucumber Suite')
      expect(writer.test_class_name).to eq('Cucumber Class')
    end

    it 'accepts a test naming strategy option' do
      writer = described_class.new(stream:, test_naming_strategy: :long)

      expect(writer.test_naming_strategy).to eq(:long)
    end

    it 'supports the streaming method names' do
      writer = described_class.new(stream:)

      expect(writer).to respond_to(:write, :update, :close)
    end

    it 'finalizes to the configured stream' do
      writer = described_class.new(stream:, suite_name: 'Cucumber & Friends')

      expect(writer.close).to be(writer)
      expect(stream.string).to include('name="Cucumber &amp; Friends"')
    end

    it 'rejects writes after finalization' do
      writer = described_class.new(stream:)
      writer.close

      expect { writer.write(nil) }.to raise_error(IOError, 'cannot write to a closed JUnit XML writer')
    end
  end
end
