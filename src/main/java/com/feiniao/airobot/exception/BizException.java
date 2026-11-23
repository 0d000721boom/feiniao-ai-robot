package com.feiniao.airobot.exception;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BizException extends RuntimeException {
    private String errorCode;
    private String errorMsg;

    public BizException(BaseExceptionInterface baseExceptionlnterface) {

        this.errorCode = baseExceptionlnterface.getErrorCode();
        this.errorMsg = baseExceptionlnterface.getErrorMsg();
    }


}
