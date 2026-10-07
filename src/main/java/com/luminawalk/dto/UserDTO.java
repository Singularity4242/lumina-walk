package com.luminawalk.dto;

import lombok.Data;

//精简用户信息
@Data
public class UserDTO {
    private Long userId;
    private String nickName;
    private String icon;
}

