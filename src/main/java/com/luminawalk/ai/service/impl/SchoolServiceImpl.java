package com.luminawalk.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.ai.entity.po.School;
import com.luminawalk.ai.mapper.SchoolMapper;
import com.luminawalk.ai.service.ISchoolService;
import org.springframework.stereotype.Service;

@Service
public class SchoolServiceImpl extends ServiceImpl<SchoolMapper, School> implements ISchoolService {
}
