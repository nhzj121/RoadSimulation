package org.example.roadsimulation.sandbox.execution.bootstrap;

import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/** Keep test configurations off the production scan; explicit fixture sources remain possible. */
public final class SandboxTestComponentFilter implements TypeFilter {
    @Override public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
        var metadata = reader.getAnnotationMetadata();
        return metadata.hasAnnotation("org.springframework.boot.test.context.TestConfiguration")
                || metadata.hasMetaAnnotation("org.springframework.boot.test.context.TestComponent");
    }
}
