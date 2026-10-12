package com.luminawalk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import static com.luminawalk.utils.RedisConstants.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        // 保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

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
        // 2. 校验验证码是否一致从redis获取验证码并校验
        // Object cacheCode = session.getAttribute("code");
        // 从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        String code = loginForm.getCode();
        if( cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误!");
        }
        // 3. 根据手机号查是否已有用户, 不为空则已有用户
        User user = query().eq("phone",phone).one();
        // 4. 无，创建新用户
        if(user == null) {
            user = createUserWithPhone(phone);
        }
        // 6. 保存用户信息到redis
        // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 7.保存信息到redis
        // 7.1 随机生成token作为登陆令牌
        String token = UUID.randomUUID().toString();
        // 7.2 将User对象转为Hashmap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 7.4 token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8 返回token
        return Result.ok(token);
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
