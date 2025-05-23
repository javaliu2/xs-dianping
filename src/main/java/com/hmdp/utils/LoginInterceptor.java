package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.SessionConstant.KEY_USER;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public boolean preHandle_session_version(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、获取session
        HttpSession session = request.getSession();
        // 2、获取session中的用户
        UserDTO userDTO = (UserDTO)session.getAttribute(KEY_USER);
        // 3、判断用户是否存在
        if (userDTO == null) {
            // 4、不存在，拦截，返回401状态码（未认证）
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 5、存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 6、放行
        return true;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return  false;
        }
        // 2、获取redis中以该token为key的用户map
        String key = RedisConstants.LOGIN_USER_KEY + token;
        // 2.1 该类不由Spring托管，因此需要采用构造函数的方式将stringRedisTemplate注入进来
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return  false;
        }
        // 3、userMap => user
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 4、保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 5、刷新有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，避免内存泄漏
        UserHolder.removeUser();
    }
}
