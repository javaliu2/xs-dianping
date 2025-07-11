package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    private static final ThreadLocal<String> tokenTL = new ThreadLocal<>();
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }

    public static void saveToken(String token) {
        tokenTL.set(token);
    }
    public static String getToken() {
        return tokenTL.get();
    }
    public static void removeToken() {
        tokenTL.remove();
    }
    public static void removeAll() {
        tl.remove();
        tokenTL.remove();
    }
}
