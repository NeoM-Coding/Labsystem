package xyz.jasenon.lab.base.compensation;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@CompensationJob(code = "laboratory-auth-reconcile", cron = "0 0 0 * * *")
public class LaboratoryAuthorizationReconcileHandler implements CompensationTaskHandler {
    private final LaboratoryMapper laboratoryMapper;
    private final LaboratoryAuthorization authorization;
    public LaboratoryAuthorizationReconcileHandler(LaboratoryMapper laboratoryMapper,
                                                    LaboratoryAuthorization authorization) {
        this.laboratoryMapper = laboratoryMapper; this.authorization = authorization;
    }
    @Override public String type() { return "laboratory-auth-reconcile"; }
    @Override public CompensationResult execute(Map<String, Object> payload) {
        int count = 0;
        for (Laboratory laboratory : laboratoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Laboratory>()
                        .isNull(Laboratory::getDeleteAt))) {
            authorization.reconcile(laboratory.getId(), laboratory.getCreateBy(), managerIds(laboratory));
            count++;
        }
        return CompensationResult.success(count, "已对账 " + count + " 个实验室授权资源");
    }
    public static Set<String> managerIds(Laboratory laboratory) {
        if (laboratory.getManager() == null) return Set.of();
        return laboratory.getManager().stream().filter(java.util.Objects::nonNull)
                .map(User::getId).filter(id -> id != null && !id.isBlank()).map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }
}
