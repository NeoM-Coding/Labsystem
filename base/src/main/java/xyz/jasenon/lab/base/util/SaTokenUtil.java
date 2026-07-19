package xyz.jasenon.lab.base.util;

import cn.dev33.satoken.stp.StpUtil;
import xyz.jasenon.lab.common.util.Pair;

public class SaTokenUtil {

    // 保证这里传入的 userId not null not empty not blank
    public static void login(String userId){
        StpUtil.login(userId);
    }

    public static String userId(){
        String userId = StpUtil.getLoginIdAsString();
        return userId;
    }

    public static Pair<String, String> token(){
        String tokenName = StpUtil.getTokenName();
        String tokenValue = StpUtil.getTokenValue();
        return Pair.of(tokenName,tokenValue);
    }

}
