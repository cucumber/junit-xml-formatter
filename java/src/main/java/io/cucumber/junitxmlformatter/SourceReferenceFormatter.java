package io.cucumber.junitxmlformatter;

import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.SourceReference;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

final class SourceReferenceFormatter {
    private final Function<String, String> uriFormatter;

    SourceReferenceFormatter(Function<String, String> uriFormatter) {
        this.uriFormatter = uriFormatter;
    }

    Optional<ClassMethodName> format(SourceReference sourceReference) {
        if (sourceReference.getJavaMethod().isPresent()) {
            return sourceReference.getJavaMethod()
                    .map(javaMethod -> new ClassMethodName(
                            javaMethod.getClassName(),
                            "%s(%s)".formatted(
                                    javaMethod.getMethodName(),
                                    String.join(",", javaMethod.getMethodParameterTypes()))));
        }
        if (sourceReference.getJavaStackTraceElement().isPresent()) {
            return sourceReference.getJavaStackTraceElement()
                    .map(javaStackTraceElement -> new ClassMethodName(
                            javaStackTraceElement.getClassName(),
                            "%s(%s%s)".formatted(
                            javaStackTraceElement.getMethodName(),
                            javaStackTraceElement.getFileName(),
                            sourceReference.getLocation().map(Location::getLine).map(line -> ":" + line).orElse("")
                    )));
        }
        if (sourceReference.getUri().isPresent()) {
            return sourceReference.getUri()
                    .map(uri -> new ClassMethodName(null, uriFormatter.apply(uri) + sourceReference.getLocation()
                            .map(location -> ":" + location.getLine())
                            .orElse("")));
        }
        return Optional.empty();
    }

    record ClassMethodName(@Nullable String className, String methodName) {

    }
}
