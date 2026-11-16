package xyz.jasenon.lab.bi.telemetry;

import xyz.jasenon.lab.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.common.exception.BusinessException;
@Component
public class BiAdministrator {
    private final AuthorizationOperations authorization;
    public BiAdministrator(AuthorizationOperations authorization) { this.authorization=authorization; }
    public void require() {
        var user=UserContextHolder.get();
        if(user==null || user.getUserId()==null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED,"请先登录");
        if(!authorization.relationsOf(SourceType.app,"global",SourceType.user,user.getUserId()).contains("super_admin"))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,"仅系统管理员可维护 BI 数据");
    }
}
