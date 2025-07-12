package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
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
    private IFollowService followService;

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
        // 这里写的有问题
        // 后面的userId应该是当前用户的id，这里的话是发表 博文的用户id，显然是不对的
        // 由此可知，有语义的变量命名是很有必要的
        Long currentUserId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(currentUserId));
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

    /**
     * 1、保存博文到数据库
     * 2、保存博文id到当前用户的所有粉丝的收件箱中
     * @param blog
     * @return
     */
    @Override
    public Long saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return null;
        }
        Long blogId = blog.getId();
        // select * from tb_follow where follow_id = currentUserId
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        if (follows != null && follows.size() > 0) {
            // 给每一个粉丝推送博文
            for (Follow follow : follows) {
                String key = RedisConstants.FEED_KEY + follow.getUserId();
                stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
            }
        }
        return blogId;
    }

    @Override
    public Object getBlogOfFollowing(Long max, Integer offset) {
        // 1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2、分页查询博文
        String key = RedisConstants.FEED_KEY + userId;
        // 3、根据博文id查询博文 zrevrangebyscore key max min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());  // 显式定义arraylist大小避免元素个数大于默认值导致扩容拷贝的开销
        // 4、计算最小时间戳minTime及其个数
        long minTime = 0;
        int cnt = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(Objects.requireNonNull(tuple.getValue())));
            long time = Objects.requireNonNull(tuple.getScore()).longValue();
            if (minTime == time) {
                cnt++;
            } else {
                minTime = time;
                cnt = 1;
            }
        }
        // 5、根据id查询博文
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        // 6、填充博文其他字段
        blogs.forEach(this::fillOtherField);
        // 7、封装数据返回
        ScrollResult sr = new ScrollResult();
        sr.setList(blogs);
        sr.setOffset(cnt);
        sr.setMinTime(minTime);
        return sr;
    }
}
