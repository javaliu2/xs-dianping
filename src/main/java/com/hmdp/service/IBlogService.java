package com.hmdp.service;

import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Blog getBlog(Long id);

    void likeBlog(Long id);

    Object queryHotBlog(Integer current);

    Object getLikesTop5(Long id);
}
