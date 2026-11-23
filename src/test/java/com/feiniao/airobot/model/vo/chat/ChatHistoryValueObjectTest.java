package com.feiniao.airobot.model.vo.chat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatHistoryValueObjectTest {

    @Test
    void pageDefaultsToOneAndTwenty() {
        PageReqVO request = new PageReqVO();

        assertEquals(1L, request.getPageNum());
        assertEquals(20L, request.getPageSize());
    }

    @Test
    void pageNumberCannotBeNull() {
        PageReqVO request = new PageReqVO();
        request.setPageNum(null);

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertFalse(validator.validate(request).isEmpty());
    }
}
