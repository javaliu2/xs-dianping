package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        log.info("[Controller]: 发送短信验证码并保存验证码");
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        // 实现登录功能
        log.info("[Controller]: 用户登录，前端参数：{}", loginForm);
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        log.info("登出【controller】【begin】");
        userService.logout();
        log.info("登出【controller】【end】");
        return Result.ok("退出成功");
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        log.info("[Controller]: in me(), user: {}", user);
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 更新用户详细信息
     * 1、bug记录
     * 1）updateById()方法不会在数据项不存在的情况下将数据insert入数据库
     * 因为其产生的sql语句是UPDATE tb_user_info SET introduce=? WHERE user_id=?
     * 当没有该数据项时，where条件判为false，所以更新失败，他也不会insert
     * 2）数据项不存在就插入，有就更新，应该使用saveOrUpdate()
     *   原理：首先select，如果查询到结果，那么执行update；否则执行insert
     */
    @PostMapping("/info")
    public Result info(@RequestBody UserInfo userInfo){
        log.info("保存用户信息：{}", userInfo);
        boolean isSuccess = userInfoService.saveOrUpdate(userInfo);
        return Result.ok(isSuccess);
    }

    @GetMapping("/{id}")
    public Result getUser(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 前端不传递任何参数，但是为什么还是要使用post方法请求
     * 答：方法具有语义，表明这次请求对后端数据有修改
     * @return
     */
    @PostMapping("/sign")
    public Result signIn() {
        log.info("用户签到");
        userService.signIn();
        return Result.ok();
    }

    /**
     * 获取当前用户截止当前日期（包括今天）的连续签到天数
     * @return
     */
    @GetMapping("/sign/count")
    public Result getContinueSignDays() {
        log.info("获取连续签到天数");
        Object data = userService.getContinueSignDays();
        return Result.ok(data);
    }

    /**
     * 上传头像，返回服务器保存的图片文件路径
     * @return
     */
    @PostMapping("/uplaod/icon")
    public Result uploadIcon(@RequestParam("file") MultipartFile file) {
        log.info("上传图片");
        Object data = userService.uploadIcon(file);
        if (data == null) {
            return Result.fail("文件上传失败");
        }
        return Result.ok(data);
    }
}
