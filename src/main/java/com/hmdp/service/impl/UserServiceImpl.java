package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.MessageConstant;
import com.hmdp.constant.SessionConstant;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import javax.xml.transform.OutputKeys;

import static com.hmdp.constant.SessionConstant.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstant.ERROR_PHONE_FORMAT);
        }
        String code = RandomUtil.randomNumbers(6);
        session.setAttribute(KEY_VERIFY_CODE, code);
        log.debug("[Service]: 发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 校验失败，返回错误信息
            return Result.fail(MessageConstant.ERROR_PHONE_FORMAT);
        }
        // 3. 校验验证码
        Object cacheCode = session.getAttribute(KEY_VERIFY_CODE);// 常量提取到一个常量值类中是工程实践
        String code = loginForm.getCode();
        if (cacheCode == null || !code.equals(String.valueOf(cacheCode))) {
            return Result.fail(MessageConstant.ERROR_VERIFY_CODE);
        }
        // 4、根据手机号查询用户
        User user = query().eq("phone", phone).one();  // mybatis-plus提供的API
        // 5、判断用户是否存在
        if (user == null) {
            user = createUser(phone);
        }
        // 6、保存用户信息到session中
        session.setAttribute(KEY_USER, user);
        return Result.ok();
    }

    private User createUser(String phone) {
        // 1、创建用户，填充属性
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(6));
        // 2、保存用户对象
        save(user);
        return user;
    }
}
