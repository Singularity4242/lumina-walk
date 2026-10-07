package com.luminawalk.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.entity.UserInfo;
import com.luminawalk.mapper.UserInfoMapper;
import com.luminawalk.service.IUserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}