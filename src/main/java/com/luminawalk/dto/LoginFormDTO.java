package com.luminawalk.dto;

import lombok.Data;


//前端传给后端的登录参数
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}