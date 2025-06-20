package com.hmdp;

import cn.hutool.json.JSONObject;
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
}
