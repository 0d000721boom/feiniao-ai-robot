package com.feiniao.airobot.aspect;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiOperationLogAspectTest {

    @Test
    void multipartArgumentsAreLoggedAsMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.md",
                "text/markdown",
                "# guide".getBytes());

        String json = ApiOperationLogAspect.describeArgument(file);

        assertTrue(json.contains("\"originalFilename\":\"guide.md\""));
        assertTrue(json.contains("\"size\":7"));
        assertFalse(json.contains("MultipartFileResource"));
    }
}
