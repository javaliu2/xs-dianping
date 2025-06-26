package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score != null) {  // 2.1 已经点赞过，将博客赞数减一，将用户id从set中移除
            update().setSql("liked = liked - 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId);
        } else {  // 2.2 没有点赞过，将博客赞数加一，将用户id加入set
            update().setSql("liked = liked + 1").eq("id", id).update();
            // 加入zset的时候，将score指定为当前时间戳
            stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
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
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
        blog.setIsLike(score != null);
    }

    @Override
    public Object getLikesTop5(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 1、返回的就是用户id按照score排序的集合 zset key 0 4
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (set == null || set.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2、解析出id
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        // 3、根据id查询用户，注意使用 id in (?, ?)的话，查询的结果是按照id升序的，而不是(?, ?)中的id顺序
        // 所以需要使用 order by field(id, ?, ?)，指定按照id排序，序列为自己指定的，即ids中的顺序
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("id", ids).last("ORDER BY FIELD (id, " + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return userDTOS;
    }
}
