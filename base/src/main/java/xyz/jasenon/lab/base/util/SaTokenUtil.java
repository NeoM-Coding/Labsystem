package xyz.jasenon.lab.base.util;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import xyz.jasenon.lab.common.util.Pair;

public final class SaTokenUtil {

    private SaTokenUtil() {
    }

    // Dubbo Provider 没有 Servlet 上下文，使用官方 Mock Context 执行完整登录流程。
    public static Pair<String, String> login(String userId){
        return SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login(userId);
            return Pair.of(StpUtil.getTokenName(), StpUtil.getTokenValue());
        });
    }

    public static String userId(){
        String userId = StpUtil.getLoginIdAsString();
        return userId;
    }

}
