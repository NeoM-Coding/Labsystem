package xyz.jasenon.lab.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.base.api.validation.ValidationErrors;
import xyz.jasenon.lab.base.api.vo.LaboratoryVO;
import xyz.jasenon.lab.base.context.UserContextFactory;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.mapper.UserMapper;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@DubboService
@Traced("laboratory-service")
public class LaboratoryServiceImpl extends ServiceImpl<LaboratoryMapper, Laboratory> implements LaboratoryService {

    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;

    private final LaboratoryAuthorization laboratoryAuthorization;
    private final UserMapper userMapper;
    private final UserContextStore userContextStore;

    public LaboratoryServiceImpl(LaboratoryAuthorization laboratoryAuthorization,
                                 UserMapper userMapper,
                                 UserContextStore userContextStore) {
        this.laboratoryAuthorization = laboratoryAuthorization;
        this.userMapper = userMapper;
        this.userContextStore = userContextStore;
    }

    @Override
    public RpcResult<List<Pair<String, String>>> collectionOrgName() {
        return RpcResult.success(this.baseMapper.collectionOrgName());
    }

    @Override
    public RpcResult<List<Pair<String, String>>> collectionBuildingName() {
        return RpcResult.success(this.baseMapper.collectionBuildingName());
    }

    @Override
    public RpcResult<List<LaboratoryVO>> list(String[] buildingNames, String[] orgNames) {
        var context = UserContextHolder.get();
        if (context == null) {
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        // 楼栋与组织过滤只能在当前用户的可见范围内继续收窄，不能扩大数据边界。
        List<String> laboratoryIds = context.filterLaboratoryIds(buildingNames, orgNames);
        if (laboratoryIds.isEmpty()) {
            return RpcResult.success(List.of());
        }
        return RpcResult.success(this.baseMapper.selectByIds(laboratoryIds).stream()
                .map(LaboratoryVO::from)
                .toList());
    }

    @Override
    @Audited("laboratory.create")
    @ActionAuthorized
    @Transactional
    public RpcResult<Laboratory> create(LaboratoryCreate command) {
        UserContext context = requireUserContext();
        Laboratory laboratory = from(command);
        ValidationErrors errors = laboratory.validate();
        if (errors.hasErrors()) {
            throw new BusinessException(BAD_REQUEST, String.join(",", errors.errors()));
        }
        save(laboratory);
        // 初始化失败时异常向外传播，由本地事务回滚数据库写入。
        laboratoryAuthorization.initialize(laboratory.getId(), context.getUserId());
        Set<String> affectedUserIds = laboratoryAuthorization.usersWhoCanView(laboratory.getId());
        afterCommit(() -> refreshUserContexts(affectedUserIds));
        return RpcResult.success(laboratory);
    }

    @Override
    @Audited("laboratory.edit")
    @ActionAuthorized
    @Transactional
    public RpcResult<Laboratory> update(LaboratoryEdit command) {
        String laboratoryId = command.laboratoryId();
        requireUserContext();
        Laboratory laboratory = from(command);
        ValidationErrors errors = laboratory.validate();
        if (errors.hasErrors()) {
            throw new BusinessException(BAD_REQUEST, String.join(",", errors.errors()));
        }
        laboratory.setId(laboratoryId);
        updateById(laboratory);
        // Context 缓存了名称、楼栋和组织，实验室信息变化后需要同步所有可见用户。
        Set<String> affectedUserIds = laboratoryAuthorization.usersWhoCanView(laboratoryId);
        afterCommit(() -> refreshUserContexts(affectedUserIds));
        return RpcResult.success(laboratory);
    }

    @Override
    @Audited("laboratory.delete")
    @ActionAuthorized
    @Transactional
    public RpcResult<Void> delete(LaboratoryDelete command) {
        String laboratoryId = command.laboratoryId();
        // 写权限由 app:global#manage_laboratory 决定，view scope 仅用于查询范围。
        requireUserContext();

        // 必须在清理 Permify 关系前反查，否则无法再得到完整的受影响用户集合。
        Set<String> affectedUserIds = laboratoryAuthorization.usersWhoCanView(laboratoryId);
        removeById(laboratoryId);
        // Permify 清理失败时抛出异常，使 MySQL 事务回滚。
        laboratoryAuthorization.remove(laboratoryId);
        // Redis 不参与数据库事务，只能在提交成功后刷新。
        afterCommit(() -> refreshUserContexts(affectedUserIds));
        return RpcResult.success();
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void refreshUserContexts(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        UserContext current = UserContextHolder.get();
        for (String userId : userIds) {
            userContextStore.find(userId)
                    .or(() -> current != null && userId.equals(current.getUserId())
                            ? Optional.of(current)
                            : Optional.empty())
                    .ifPresent(existing -> refreshUserContext(userId, existing));
        }
    }

    private void refreshUserContext(String userId, UserContext existing) {
        var user = userMapper.selectById(userId);
        if (user == null) {
            userContextStore.delete(userId);
            return;
        }
        Set<String> visibleLaboratoryIds = laboratoryAuthorization.visibleLaboratoryIds(userId);
        List<Laboratory> visibleLaboratories = visibleLaboratoryIds.isEmpty()
                ? List.of()
                : this.baseMapper.selectByIds(visibleLaboratoryIds);
        UserContext refreshed = UserContextFactory.from(user, visibleLaboratories);
        refreshed.setLoginAt(existing.getLoginAt());
        userContextStore.save(refreshed);

        UserContext current = UserContextHolder.get();
        if (current != null && userId.equals(current.getUserId())) {
            UserContextHolder.set(refreshed);
        }
    }

    private static UserContext requireUserContext() {
        UserContext context = UserContextHolder.get();
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()) {
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        return context;
    }

    private static Laboratory from(LaboratoryCreate command) {
        Laboratory laboratory = new Laboratory();
        laboratory.setBuildingName(command.buildingName());
        laboratory.setOrgName(command.orgName());
        laboratory.setLaboratoryName(command.laboratoryName());
        laboratory.setExtra(command.extra());
        laboratory.setManager(command.manager());
        return laboratory;
    }

    private static Laboratory from(LaboratoryEdit command) {
        Laboratory laboratory = new Laboratory();
        laboratory.setBuildingName(command.buildingName());
        laboratory.setOrgName(command.orgName());
        laboratory.setLaboratoryName(command.laboratoryName());
        laboratory.setExtra(command.extra());
        laboratory.setManager(command.manager());
        return laboratory;
    }
}
