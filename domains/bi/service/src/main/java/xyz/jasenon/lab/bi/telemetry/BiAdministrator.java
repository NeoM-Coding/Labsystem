package xyz.jasenon.lab.bi.telemetry;
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
        if(user==null || user.getUserId()==null) throw new BusinessException(401,"请先登录");
        if(!authorization.relationsOf(SourceType.app,"global",SourceType.user,user.getUserId()).contains("super_admin"))
            throw new BusinessException(403,"仅系统管理员可维护 BI 数据");
    }
}
