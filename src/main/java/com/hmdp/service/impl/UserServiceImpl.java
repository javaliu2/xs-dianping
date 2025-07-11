package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.MessageConstant;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstant.ERROR_PHONE_FORMAT);
        }
        String code = RandomUtil.randomNumbers(6);
        // 1、采用session作为会话信息的存储
//        session.setAttribute(KEY_VERIFY_CODE, code);  // 每一个用户一个会话，因此使用相同的key互不妨碍
        // 2、采用redis作为会话信息的存储，所有用户共享一个redis存储，因此需要使用不同的key，而手机号唯一正好作为key
        // 另外，加前缀使得手机号码有意义，便于开发维护
        // 需要设置有效期，防止redis存储空间不够用
        String key = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("[Service]: 发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 校验失败，返回错误信息
            return Result.fail(MessageConstant.ERROR_PHONE_FORMAT);
        }
        // 3. 校验验证码
        // 3.1 session存储会话信息
//        Object cacheCode = session.getAttribute(KEY_VERIFY_CODE);// 常量提取到一个常量值类中是工程实践
        // 3.2 redis存储
        String key = LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(key);
        String code = loginForm.getCode();
        if (!code.equals(cacheCode)) {
            return Result.fail(MessageConstant.ERROR_VERIFY_CODE);
        }
        // 4、根据手机号查询用户
        User user = query().eq("phone", phone).one();  // mybatis-plus提供的API
        // 5、判断用户是否存在
        if (user == null) {
            // 不存在的话，创建用户
            user = createUser(phone);
        }
        // 6、保存用户信息到redis中
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setIcon(user.getIcon());
        userDTO.setNickName(user.getNickName());
        // 6.1 session存储
//        session.setAttribute(KEY_USER, userDTO);
        // 6.2 redis存储
        // 1) value: 需要将userDTO对象转为map存储
        Map<String, String> userDTOMap = bean2Map(userDTO);
        log.debug("bean2Map() test, 对象: {}, map存储: {}", userDTO, userDTOMap);
        // 2) key: 采用语义化前缀 + UUID
        String token = UUID.randomUUID().toString();
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userDTOMap);
        // 3) 设置数据有效期（不设置有效期，方便调试）
//        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 7、将token返回给前端，使得以后每次请求都携带该token以表明身份
        return Result.ok(token);
    }

    private Map<String, String> bean2Map(Object bean) {
        Map<String, String> map = new HashMap<>();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                String propertyName = descriptor.getName();
                if (!"class".equals(propertyName)) {
                    Object value = descriptor.getReadMethod().invoke(bean);
                    if (value != null) {
                        map.put(propertyName, String.valueOf(value));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Bean to Map<String, String> conversion error", e);
        }
        return map;
    }


    private User createUser(String phone) {
        // 1、创建用户，填充属性
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(6));
        // 2、保存用户对象
        log.info("未save前，user.id: {}", user.getId());  // 未save前，user.id: null
        save(user);
        log.info("save后，user.id: {}", user.getId());  // save后，user.id: 1073
        return user;
    }

    @Override
    public void logout() {
        // 获取当前用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            log.warn("登出失败：当前用户为空");
            return;
        }

        // 获取请求上下文中的 token（如果你没从 controller 传 token 进来，这里用 request 提取）
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) return;

        HttpServletRequest request = requestAttributes.getRequest();
        String token = request.getHeader("Authorization");
        if (StrUtil.isBlank(token)) {
            log.warn("登出失败：token 为空");
            return;
        }

        // 构造 Redis key 并删除
        String redisKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(redisKey);

        // 清除 ThreadLocal 中的用户
        UserHolder.removeUser();
        log.info("登出成功，token: {}", token);
    }

    @Override
    public void signIn() {
        // 1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2、通过redis bitmap保存用户签到信息
        // tips: 由于redis bitmap底层是string，故其api调用的是opsForValue
        // 2.1、获取key
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 2.2、这一天是该月中第几天
        int dayOfMonth = now.getDayOfMonth();
        // 2.3、设置bitmap
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);  // 不去减1，第一位空着
    }

    @Override
    public Object getContinueSignDays() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        // 获取当前用户本月截止今日的签到数据 bitfield key get type offset
        // 由于bitfield支持多条子命令，所以返回结果是一个list
        List<Long> results = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(1));// 索引从1开始查
        if (results == null || results.isEmpty()) {
            return null;
        }
        // 统计连续签到天数，从最后一天开始查询
        Long signCount = results.get(0);
        int count = 0;
        while (signCount != 0) {
            if ((signCount & 1) == 1) {
                count++;
            } else {
                break;
            }
            signCount >>= 1;
        }
        return count;
    }

    @Override
    public Object uploadIcon(MultipartFile file) {
        if (file.isEmpty()) {
            return null;
        }
        String originalFilename = file.getOriginalFilename();  // 就是 图片文件名.type
//        System.out.println("originalFilename: " + originalFilename);  // originalFilename: 油茶面.jpeg
        log.info("originalFilename: {}", originalFilename);
        // 保存到 /var/www/html/hmdp/imgs/icons/
        String savePath = "/var/www/html/hmdp/imgs/icons/";
        assert originalFilename != null;
        String suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
        String filename = UUID.randomUUID() + suffix;
        log.info("new filename: {}", filename);
        String iconPath = "/imgs/icons/" + filename;
        File dest = new File(savePath + filename);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        // 修改UserHolder中的UserDTO对象的icon属性
        // 有问题。因为下次请求是新的线程，从redis中读取用户信息。
        // 这次请求的UserHolder虽然修改，但是在下次请求的时候会失效
        UserDTO userDTO = UserHolder.getUser();
//        userDTO.setIcon(iconPath);
        // 修改redis中用户信息缓存
        String token = UserHolder.getToken();
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().put(key, "icon", iconPath);
        // 更新数据库
        User user = new User();
        user.setId(userDTO.getId());
        user.setIcon(iconPath);
        updateById(user);
        return iconPath;
    }
}
