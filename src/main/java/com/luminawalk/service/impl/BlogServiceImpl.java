package com.luminawalk.service.impl;

import com.luminawalk.entity.Blog;
import com.luminawalk.mapper.BlogMapper;
import com.luminawalk.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 *  服务实现类
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
