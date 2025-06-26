package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
@Slf4j
public class FollowController {

    @Autowired
    private IFollowService followService;
    @PutMapping("/{id}/{flag}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("flag") boolean flag) {
        log.info("关注或者取关，flag:{}", flag);
        followService.follow(id, flag);
        return Result.ok("成功关注");
    }

    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long id) {
        log.info("是否关注，用户id:{}", id);
        boolean flag = followService.isFollow(id);
        return Result.ok(flag);
    }
}
