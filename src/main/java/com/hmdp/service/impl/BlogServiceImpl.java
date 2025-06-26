package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Blog getBlog(Long id) {
        Blog blog = query().eq("id", id).one();
        fillOtherField(blog);
        return blog;
    }

    @Override
    public void likeBlog(Long id) {
        // 1、获取当前用户
        String userId = String.valueOf(UserHolder.getUser().getId());
        // 2、查询redis set判断当前用户是否给当前博文点赞过
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Boolean exist = stringRedisTemplate.opsForSet().isMember(key, userId);
        if (Boolean.TRUE.equals(exist)) {  // 2.1 已经点赞过，将博客赞数减一，将用户id从set中移除
            update().setSql("liked = liked - 1").eq("id", id).update();
            stringRedisTemplate.opsForSet().remove(key, userId);
        } else {  // 2.2 没有点赞过，将博客赞数加一，将用户id加入set
            update().setSql("liked = liked + 1").eq("id", id).update();
            stringRedisTemplate.opsForSet().add(key, userId);
        }
    }

    @Override
    public Object queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 填充其他字段
        records.forEach(this::fillOtherField);
        return records;
    }

    private void fillOtherField(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Boolean exist = stringRedisTemplate.opsForSet().isMember(key, String.valueOf(userId));
        if (Boolean.TRUE.equals(exist)) {
            blog.setIsLike(true);
        } else {
            blog.setIsLike(false);
        }
    }
}
