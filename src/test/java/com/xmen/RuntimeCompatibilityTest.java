package com.xmen;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keep application bytecode readable by Spring when updating the bundled JDK. */
class RuntimeCompatibilityTest {
  @Test void applicationClassesRemainReadableBySpring() throws Exception {
    var resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath*:com/xmen/**/*.class");
    assertTrue(resources.length > 0);
    var reader = new CachingMetadataReaderFactory();
    for (var resource : resources) {
      assertDoesNotThrow(() -> reader.getMetadataReader(resource), resource.toString());
    }
  }
}
