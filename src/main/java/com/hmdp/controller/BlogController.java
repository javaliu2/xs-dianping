package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
@Slf4j
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        Long id = blogService.saveBlog(blog);
        if (id == null) {
            return Result.fail("保存失败");
        }
        // 返回id
        return Result.ok(id);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        log.info("给id为{}的博文点赞", id);
        blogService.likeBlog(id);
        return Result.ok();
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("查询第{}页的博文", current);
        Object data = blogService.queryHotBlog(current);
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result viewBlog(@PathVariable(name="id") Long id) {
        Blog blog = blogService.getBlog(id);
        return Result.ok(blog);
    }

    /**
     * 每篇博客点赞查询前五名
     * @param id
     * @return
     */
    @GetMapping("likes/{id}")
    public Result likesTop5(@PathVariable(name="id") Long id) {
        log.info("获取id为{}博文的点赞前五名", id);
        Object data = blogService.getLikesTop5(id);
        return Result.ok(data);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                    @RequestParam("id") Long id) {

        Page<Blog> pages = blogService.query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = pages.getRecords();
        return Result.ok(records);
    }

    /**
     * 获取当前用户关注的所有用户发表的探店博文
     * @return
     */
    @GetMapping("/of/follow")
    public Result getBlogOfFollowing(@RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        log.info("获取当前用户关注的所有用户发表的博文，前端参数：offset:{}, max:{}", offset, max);
        Object data = blogService.getBlogOfFollowing(max, offset);
        return Result.ok(data);
    }
}
