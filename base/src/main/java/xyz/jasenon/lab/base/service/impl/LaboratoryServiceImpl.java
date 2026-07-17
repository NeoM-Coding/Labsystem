package xyz.jasenon.lab.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.base.api.validation.ValidationErrors;
import xyz.jasenon.lab.base.api.model.Laboratory;
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
    public Laboratory create(Laboratory laboratory) {
        ValidationErrors errors = laboratory.validate();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }
        save(laboratory);
        return laboratory;
    }

    @Override
    public Laboratory update(String laboratoryId, Laboratory laboratory) {
        var ctx = UserContextHolder.get();
        if (ctx == null){
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        if (!ctx.canViewLaboratory(laboratoryId)) {
            throw new BusinessException(FORBIDDEN, "你无权修改此实验室");
        }
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
    public void delete(String laboratoryId) {
        var ctx = UserContextHolder.get();
        if (ctx == null){
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        if (!ctx.canViewLaboratory(laboratoryId)) {
            throw new BusinessException(FORBIDDEN, "你无权删除此实验室");
        }
        removeById(laboratoryId);
    }
}
