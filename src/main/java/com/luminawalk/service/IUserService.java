package com.luminawalk.service;

import com.luminawalk.dto.LoginFormDTO;
import com.luminawalk.dto.Result;
import com.luminawalk.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
