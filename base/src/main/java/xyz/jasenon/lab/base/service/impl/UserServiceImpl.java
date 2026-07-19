package xyz.jasenon.lab.base.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;
import xyz.jasenon.lab.auth.permission.RelationShip;
import xyz.jasenon.lab.auth.service.Auth;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.mapper.UserMapper;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.context.UserContextFactory;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.base.util.SaTokenUtil;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.base.api.validation.ValidationErrors;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.Set;

@DubboService
@Traced("user-service")
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_SERVER_ERROR = 500;

    private final Auth auth;
    private final LaboratoryMapper laboratoryMapper;
    private final UserContextStore userContextStore;

    public UserServiceImpl(Auth auth,
                           LaboratoryMapper laboratoryMapper,
                           UserContextStore userContextStore) {
        this.auth = auth;
        this.laboratoryMapper = laboratoryMapper;
        this.userContextStore = userContextStore;
    }

    @Override
    public boolean existsByName(String name) {
        return this.baseMapper.isNameExsist(name);
    }

    @Override
    @Traced(value = "user-service.login", recordArgs = false)
    public UserSession login(String username, String pwd) {
        User user = this.baseMapper.getUserByUsername(username);
        if (user == null){
            throw new BusinessException(NOT_FOUND, "用户不存在!");
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()){
            throw new BusinessException(FORBIDDEN, "该用户不允许使用系统，请联系管理员!");
        }

        if (!BCrypt.checkpw(pwd,user.getPassword())){
            throw new BusinessException(FORBIDDEN, "密码错误!");
        }
        userContextStore.save(buildUserContext(user));
        SaTokenUtil.login(user.getId());
        var token = SaTokenUtil.token();
        return new UserSession(user.mask(), token.f, token.s);
    }

    @Override
    @ActionAuthorized
    @Audited("user.create")
    @Transactional
    public User registerNormalUser(UserCreate command) {
        User normalUser = User.builder()
                .name(command.name())
                .username(command.username())
                .password(command.password())
                .phone(command.phone())
                .email(command.email())
                .mark(command.mark())
                .build();
        ValidationErrors errors = normalUser.validateNormalUser();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }

        synchronized (normalUser.getName().intern()){
            boolean exsist = existsByName(normalUser.getName());
            if (exsist) {
                throw new BusinessException(FORBIDDEN, "该姓名已被使用");
            }
            try {
                save(normalUser);
            }catch (Exception e){
                throw new BusinessException(INTERNAL_SERVER_ERROR, "插入失败");
            }
        }
        synchronizeAuthorization(
                normalUser.getId(), command.appRelations(), command.laboratoryIds()
        );
        return normalUser;
    }

    @Override
    @ActionAuthorized
    @Audited("contact.create")
    @Transactional
    public User registerContactUser(ContactUserCreate command) {
        User contactUser = User.builder()
                .name(command.name())
                .phone(command.phone())
                .email(command.email())
                .mark(command.mark())
                .build();
        ValidationErrors errors = contactUser.validateContactUser();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }

        synchronized (contactUser.getName().intern()){
            boolean exsist = existsByName(contactUser.getName());
            if (exsist){
                throw new BusinessException(FORBIDDEN, "该姓名已被使用");
            }
            try {
                save(contactUser);
            }catch (Exception e){
                throw new BusinessException(INTERNAL_SERVER_ERROR, "插入失败");
            }
        }
        return contactUser;
    }

    @Override
    @ActionAuthorized
    @Audited("user.update")
    @Transactional
    public User updateUser(UserAuthorizationUpdate command) {
        User user = command.user();
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new BusinessException(BAD_REQUEST, "用户 ID 不能为空");
        }
        User existing = getById(user.getId());
        if (existing == null) {
            throw new BusinessException(NOT_FOUND, "用户不存在");
        }
        if (isBlank(existing.getUsername()) && isBlank(existing.getPassword())) {
            throw new BusinessException(BAD_REQUEST, "联系人不能通过用户更新接口分配登录信息或权限");
        }
        if (!updateById(user)) {
            throw new BusinessException(NOT_FOUND, "用户不存在");
        }
        synchronizeAuthorization(user.getId(), command.appRelations(), command.laboratoryIds());
        User updated = getById(user.getId());
        userContextStore.save(buildUserContext(updated));
        return user;
    }

    private void synchronizeAuthorization(String userId,
                                          Set<RelationShip.App> appRelations,
                                          Set<String> laboratoryIds) {
        auth.synchronize(new UserAuthorizationCommand(userId, appRelations, laboratoryIds));
    }

    private UserContext buildUserContext(User user) {
        // 查询 can_view 而不是直接 viewer tuple，确保 super_admin 等 DSL 继承权限也进入 view scope。
        Set<String> laboratoryIds = auth.visibleLaboratoryIds(user.getId());
        var laboratories = laboratoryIds.isEmpty()
                ? java.util.List.<xyz.jasenon.lab.base.api.model.Laboratory>of()
                : laboratoryMapper.selectByIds(laboratoryIds);
        return UserContextFactory.fromIdsAndLaboratories(user, laboratoryIds, laboratories);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }


}
