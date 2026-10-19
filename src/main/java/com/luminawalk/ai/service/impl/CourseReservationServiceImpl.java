package com.luminawalk.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.ai.entity.po.CourseReservation;
import com.luminawalk.ai.mapper.CourseReservationMapper;
import com.luminawalk.ai.service.ICourseReservationService;
import org.springframework.stereotype.Service;

@Service
public class CourseReservationServiceImpl extends ServiceImpl<CourseReservationMapper, CourseReservation> implements ICourseReservationService {
}
