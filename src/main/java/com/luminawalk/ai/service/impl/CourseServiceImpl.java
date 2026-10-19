package com.luminawalk.ai.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.ai.entity.po.Course;
import com.luminawalk.ai.mapper.CourseMapper;
import com.luminawalk.ai.service.ICourseService;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService{
}
