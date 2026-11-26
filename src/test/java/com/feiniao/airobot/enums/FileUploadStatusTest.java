package com.feiniao.airobot.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUploadStatusTest {

    @Test
    void mergingStatusUsesAStableCode() {
        assertEquals(5, AiCustomerServiceFileStatusEnum.MERGING.getCode());
    }
}
