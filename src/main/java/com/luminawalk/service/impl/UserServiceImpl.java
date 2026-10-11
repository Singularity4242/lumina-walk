package com.luminawalk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.luminawalk.dto.LoginFormDTO;
import com.luminawalk.dto.Result;
import com.luminawalk.dto.UserDTO;
import com.luminawalk.entity.User;
import com.luminawalk.mapper.UserMapper;
import com.luminawalk.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.luminawalk.utils.SystemConstants.USER_NICK_NAME_PREFIX;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        session.setAttribute("code", code);
        // TODO:发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        // 2. 校验验证码是否一致
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if( cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误!");
        }
        // 3. 根据手机号查是否已有用户, 不为空则已有用户
        User user = query().eq("phone",phone).one();
        // 4. 无，创建新用户
        if(user == null) {
            user = createUserWithPhone(phone);
        }
        // 6. 保存用户信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        // 1.创建
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存
        save(user);
        return user;
    }
}
