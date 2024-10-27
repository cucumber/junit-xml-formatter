package io.cucumber.junitxmlformatter.cli;

import picocli.CommandLine.IVersionProvider;

import java.util.Optional;
import java.util.function.Function;

import static io.cucumber.junitxmlformatter.cli.JunitXmlFormatter.JUNIT_XML_FORMATTER_NAME;

public class ManifestVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        var version = getAttribute(Package::getImplementationVersion).orElse("DEVELOPMENT");
        return new String[]{JUNIT_XML_FORMATTER_NAME + " " + version};
    }

    private static Optional<String> getAttribute(Function<Package, String> function) {
        return Optional.ofNullable(ManifestVersionProvider.class.getPackage()).map(function);
    }
}
