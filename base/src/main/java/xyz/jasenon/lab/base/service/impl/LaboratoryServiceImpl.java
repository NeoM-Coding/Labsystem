package xyz.jasenon.lab.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.base.api.validation.ValidationErrors;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.vo.LaboratoryVO;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;

@DubboService
@Traced("laboratory-service")
public class LaboratoryServiceImpl extends ServiceImpl<LaboratoryMapper, Laboratory> implements LaboratoryService {

    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    @Override
    public List<Pair<String, String>> collectionOrgName() {
        return this.baseMapper.collectionOrgName();
    }

    @Override
    public List<Pair<String, String>> collectionBuildingName() {
        return this.baseMapper.collectionBuildingName();
    }

    @Override
    public List<LaboratoryVO> list(String buildingName, String orgName) {
        var context = UserContextHolder.get();
        if (context == null) {
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        // 楼栋与组织过滤只能在当前用户的可见范围内继续收窄，不能扩大数据边界。
        List<String> laboratoryIds = context.filterLaboratoryIds(buildingName, orgName);
        if (laboratoryIds.isEmpty()) {
            return List.of();
        }
        return this.baseMapper.selectByIds(laboratoryIds).stream()
                .map(LaboratoryVO::from)
                .toList();
    }

    @Override
    @Audited("laboratory.create")
    @ActionAuthorized
    public Laboratory create(LaboratoryCreate command) {
        Laboratory laboratory = from(command);
        ValidationErrors errors = laboratory.validate();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }
        save(laboratory);

        // todo permify grant 权限

        return laboratory;
    }

    @Override
    @Audited("laboratory.edit")
    @ActionAuthorized
    public Laboratory update(LaboratoryEdit command) {
        String laboratoryId = command.laboratoryId();
        var ctx = UserContextHolder.get();
        if (ctx == null){
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        if (!ctx.canViewLaboratory(laboratoryId)) {
            throw new BusinessException(FORBIDDEN, "你无权修改此实验室");
        }
        Laboratory laboratory = from(command);
        ValidationErrors errors = laboratory.validate();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }
        laboratory.setId(laboratoryId);
        updateById(laboratory);
        return laboratory;
    }

    @Override
    @Audited("laboratory.delete")
    @ActionAuthorized
    public void delete(LaboratoryDelete command) {
        String laboratoryId = command.laboratoryId();
        var ctx = UserContextHolder.get();
        if (ctx == null){
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        if (!ctx.canViewLaboratory(laboratoryId)) {
            throw new BusinessException(FORBIDDEN, "你无权删除此实验室");
        }
        removeById(laboratoryId);

        // todo permify 回收资源
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
