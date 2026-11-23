package com.feiniao.airobot;

import com.feiniao.airobot.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
    //通用码
    SYSTEM_ERROR("10000","出错啦，后台正在修复中"),
    PARAM_NOT_VALID("10001","参数错误"),
    //业务异常码
    CHAT_NOT_FOUND("20001","对话不存在"),
    CHAT_TITLE_INVALID("20002","对话标题去除首尾空格后必须为 1-50 个字符")
    ;

    //异常码和错误信息
    private String errorCode;
    private String errorMsg;
}
