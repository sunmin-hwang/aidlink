package io.aidlink;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NonWebApplicationContextTest {

    @Test
    void loadsForOneShotBatchExecution() {
        // A non-web context is required by one-shot Spring Batch imports.
    }
}
