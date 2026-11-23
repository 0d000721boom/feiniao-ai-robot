package com.feiniao.airobot.utils;

import com.feiniao.airobot.exception.BaseExceptionInterface;
import com.feiniao.airobot.exception.BizException;
import lombok.Data;

import java.io.Serializable;

/*
* @description:参数响应工具类
* */
@Data
public class Response<T> implements Serializable {
    //默认为true
    private boolean success = true;
    //响应消息和异常信息
    private String errorCode;
    private String Msg;
    private T data;
    //成功响应
    public static<T> Response<T> success() {
        Response<T> response = new Response<T>();
        return  response;
    }

    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setData(data);
        return response;
    }
    //失败响应
    public static <T> Response<T> fail(String errorMessage) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setMsg(errorMessage);
        return response;
    }

    public static <T> Response<T> fail(String errorCode, String errorMessage) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setMsg(errorMessage);
        return response;
    }

    public static <T> Response<T> fail(BizException bizException) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setErrorCode(bizException.getErrorCode());
        response.setMsg(bizException.getErrorMsg());
        return response;
    }

    public static <T> Response<T> fail(BaseExceptionInterface baseExceptionInterface) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setErrorCode(baseExceptionInterface.getErrorCode());
        response.setMsg(baseExceptionInterface.getErrorMsg());
        return response;
    }
}
