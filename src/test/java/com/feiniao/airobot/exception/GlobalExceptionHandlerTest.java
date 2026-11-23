package com.feiniao.airobot.exception;

import com.feiniao.airobot.ResponseCodeEnum;
import com.feiniao.airobot.utils.Response;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void bindExceptionUsesParameterErrorCodeAndFieldMessage() {
        PageRequest requestValue = new PageRequest();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(requestValue, "pageReqVO");
        bindingResult.rejectValue("pageNum", "Min", "页码不能小于 1");
        BindException exception = new BindException(bindingResult);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRequestURI()).thenReturn("/chat/history");

        Response<Object> response =
                new GlobalExceptionHandler().handleBindException(servletRequest, exception);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        assertTrue(response.getMsg().contains("pageNum 页码不能小于 1"));
    }

    private static final class PageRequest {
        private Long pageNum;

        @SuppressWarnings("unused")
        public Long getPageNum() {
            return pageNum;
        }

        @SuppressWarnings("unused")
        public void setPageNum(Long pageNum) {
            this.pageNum = pageNum;
        }
    }
}
