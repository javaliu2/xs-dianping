package com.hmdp;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * 自动化生成1000个用户token
 * 保存到文件
 */
@SpringBootTest
public class ProductBatchUserToken {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 1000个token，若干个用户，因为手机号有重复
     * @throws IOException
     */
    @Test
    void batchProductToken() throws IOException {
        final int user_num = 1000;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < user_num; i++) {
            // 1、发送验证码
            // 1.1 构造电话号码
            long l = System.currentTimeMillis();
            String s = String.valueOf(l);
//        System.out.println(s);  // 1750330883810
            String phone = s.substring(0, 11);
            // 1.2 通过HttpClient向本机服务发起请求
            CloseableHttpClient httpClient = HttpClients.createDefault();
            String uri = "http://localhost:80/api/user/code";
            HttpPost post = new HttpPost(uri);
            // 设置表单参数
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("phone", phone));
            post.setEntity(new UrlEncodedFormEntity(params));
            try {
                CloseableHttpResponse response = httpClient.execute(post);
                System.out.println("状态码：" + response.getCode());
                System.out.println("响应：" + new String(response.getEntity().getContent().readAllBytes()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // 2、登录
            // 2.1 从redis拿到验证码，构造LoginFormDTO对象，发起请求
            String key = RedisConstants.LOGIN_CODE_KEY + phone;
            String auth_code = stringRedisTemplate.opsForValue().get(key);
            uri = "http://localhost:80/api/user/login";
            JSONObject json = new JSONObject();
            json.put("phone", phone);
            json.put("code", auth_code);
            String jsonBody = json.toString();
            post = new HttpPost(uri);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(jsonBody));
            try {
                CloseableHttpResponse response = httpClient.execute(post);
                System.out.println("状态码：" + response.getCode());
                String responseJson = new String(response.getEntity().getContent().readAllBytes());
                JSONObject jsonObject = new JSONObject(responseJson);
                String token = (String) jsonObject.get("data");
                // 3、将返回结果中的token保存
                tokens.add(token);
                System.out.println("响应：" + responseJson);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 4、将tokens写入文件
        Path file = Paths.get("tokens");
        Files.write(file, tokens);
    }

    /**
     * 1000个用户
     * @throws IOException
     */
    @Test
    void batchProductToken2() throws IOException {
        final int user_num = 1000;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < user_num; i++) {
            // 1、发送验证码
            // 1.1 构造电话号码
            String phone = "138" + String.format("%08d", i);
            // 1.2 通过HttpClient向本机服务发起请求
            CloseableHttpClient httpClient = HttpClients.createDefault();
            String uri = "http://localhost:80/api/user/code";
            HttpPost post = new HttpPost(uri);
            // 设置表单参数
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("phone", phone));
            post.setEntity(new UrlEncodedFormEntity(params));
            try {
                CloseableHttpResponse response = httpClient.execute(post);
                System.out.println("状态码：" + response.getCode());
                System.out.println("响应：" + new String(response.getEntity().getContent().readAllBytes()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // 2、登录
            // 2.1 从redis拿到验证码，构造LoginFormDTO对象，发起请求
            String key = RedisConstants.LOGIN_CODE_KEY + phone;
            String auth_code = stringRedisTemplate.opsForValue().get(key);
            uri = "http://localhost:80/api/user/login";
            JSONObject json = new JSONObject();
            json.put("phone", phone);
            json.put("code", auth_code);
            String jsonBody = json.toString();
            post = new HttpPost(uri);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(jsonBody));
            try {
                CloseableHttpResponse response = httpClient.execute(post);
                System.out.println("状态码：" + response.getCode());
                String responseJson = new String(response.getEntity().getContent().readAllBytes());
                JSONObject jsonObject = new JSONObject(responseJson);
                String token = (String) jsonObject.get("data");
                // 3、将返回结果中的token保存
                tokens.add(token);
                System.out.println("响应：" + responseJson);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 4、将tokens写入文件
        Path file = Paths.get("tokens2");
        Files.write(file, tokens);
    }
    // 没有注入，报null
    @Autowired
    private IUserService userService;
    /**
     * 删除redis中批量插入的token
     */
    @Test
    void deleteBatchToken() throws IOException {
        // 1. 读取 token 文件中的所有 token
        String path = "/home/xs/basic_skill/dianping/xs-dianping/tokens2";
        List<String> tokens = Files.readAllLines(Paths.get(path))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toList());

        // 2. 拼接 Redis key
        List<String> redisKeys = tokens.stream()
                .map(token -> LOGIN_USER_KEY + token)
                .collect(Collectors.toList());
        // 3. 删除数据库中tb_user表数据
        // 操作的是hash类型的值，所以对应的命令式 hget key field
//        List<Long> ids = new ArrayList<>();
//        每次请求都是一个连接，配置文件中max-active是10，所以报错io.lettuce.core.RedisException: Connection closed
//        for (String key : redisKeys) {
//            String id_str = (String) stringRedisTemplate.opsForHash().get(key, "id");
//            assert id_str != null;
//            ids.add(Long.parseLong(id_str));
//        }
//        userService.removeByIds(ids);
        // 获取第一个插入用户的id和最后一个用户的id，因为插入的数据用户id是递增的，所以可以使用两个数，确定插入id的区间
        String key_begin = redisKeys.get(0), key_end = redisKeys.get(redisKeys.size() - 1);
        String id_begin_str = (String) stringRedisTemplate.opsForHash().get(key_begin, "id");
        String id_end_str = (String) stringRedisTemplate.opsForHash().get(key_end, "id");
        Long id_begin = Long.parseLong(id_begin_str), id_end = Long.parseLong(id_end_str);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.between("id", id_begin, id_end);
        userService.remove(wrapper);
        // 4. 删除 Redis 中对应的 key
        Long deleted = stringRedisTemplate.delete(redisKeys);
        System.out.println("成功删除 Redis 中的 token key 数量: " + deleted);
    }
}
