package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Override
    public void follow(Long targetId, boolean flag) {
        // 1、当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2、构建数据库记录
        Follow follow_record = new Follow();
        follow_record.setUserId(userId);
        follow_record.setFollowUserId(targetId);
        // 3、判断是关注还是取关
        if (flag) {  // 关注
            // insert into tb_follow (user_id, follow_user_id) values (?, ?)
            save(follow_record);
        } else {
            // 取关，delete from tb_follow where user_id = ? and follow_user_id = ?
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", targetId));
        }
    }

    @Override
    public boolean isFollow(Long targetId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", targetId).count();
        return count == 1;
    }
}
