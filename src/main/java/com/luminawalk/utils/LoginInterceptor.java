package com.luminawalk.utils;

import com.luminawalk.dto.UserDTO;
import com.luminawalk.entity.User;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            log.info("用户为空");
            // 没有，拦截
            response.setStatus(401);
            return false;
        }
        // 有，放行
        return true;
    }
}