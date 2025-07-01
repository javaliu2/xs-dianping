package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static Result ok(){
        return new Result(true, null, null, null);
    }

    /**
     * 错误复现：前端输入手机号和验证码登录成功之后，再次点击‘我的’，重新跳转回登录页面
     * 原因：下面的ok(String message)方法是我写的，后端返回 Result.ok(token)时，应该调用ok(Object data)方法
     * 但是因为是重载，所以他选择了ok(String message)方法，导致前端收到的json长这样：
     * {"success":true,"errorMsg":"f26241ba-a1af-4a64-beae-e495f513fd4f"}
     */
    public static Result okWithMessage(String message){
        return new Result(true, message, null, null);
    }
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
