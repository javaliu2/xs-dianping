package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    @Override
    public void follow(Long targetId, boolean flag) {
        // 1、当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2、构建数据库记录
        Follow follow_record = new Follow();
        follow_record.setUserId(userId);
        follow_record.setFollowUserId(targetId);
        // 3、判断是关注还是取关
        // 新增，当前用户关注的目标用户，为了获取common关注使用
        String key = RedisConstants.USER_FOLLOW_KEY + userId;
        if (flag) {  // 关注
            // insert into tb_follow (user_id, follow_user_id) values (?, ?)
            boolean isSuccess = save(follow_record);
            if (isSuccess) {
                // sadd key value1 value2
                stringRedisTemplate.opsForSet().add(key, String.valueOf(targetId));
            }
        } else {
            // 取关，delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", targetId));
            if (isSuccess) {
                // srem key value1 value2
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(targetId));
            }
        }
    }

    @Override
    public boolean isFollow(Long targetId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", targetId).count();
        return count == 1;
    }

    @Override
    public Object getCommonFollow(Long id) {
        // 1、登录用户关注的用户列表
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.USER_FOLLOW_KEY + userId;
        // 2、获取两者的common用户id
        String key2 = RedisConstants.USER_FOLLOW_KEY + id;
        Set<String> intersectUserIds = stringRedisTemplate.opsForSet().intersect(key, key2);
        // 这种写法不好，会有多次数据库查询，应该使用批量查询
        // 另外，反向判断，条件不成立，就直接返回
//        List<UserDTO> userDTOS = new ArrayList<>();
//        if (intersectUserIds != null && !intersectUserIds.isEmpty()) {
//            for (String id_str : intersectUserIds) {
//                User user = userService.getById(id_str);
//                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//                userDTOS.add(userDTO);
//            }
//        }
        if (intersectUserIds == null || intersectUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = intersectUserIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return userDTOS;
    }
}
