package com.luminawalk.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminawalk.dto.Result;
import com.luminawalk.entity.Blog;
import com.luminawalk.entity.User;
import com.luminawalk.service.IBlogService;
import com.luminawalk.service.IUserService;
import com.luminawalk.utils.SystemConstants;
import com.luminawalk.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    // 发布blog保存
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        User user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    // 点赞blog
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable Long id) {
        blogService.update().setSql("liked = liked + 1").eq("id",id).update();
        return Result.ok();
    }

    // 查看本人blog
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        User user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    //
    @GetMapping("/hot")
    public Result queryHotBlot(@RequestParam (value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query().orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());
        });
        return Result.ok(records);
    }
}
