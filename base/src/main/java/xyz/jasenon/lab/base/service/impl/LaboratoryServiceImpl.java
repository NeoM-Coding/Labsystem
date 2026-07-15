package xyz.jasenon.lab.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.http.HttpStatus;
import xyz.jasenon.lab.base.context.Holder;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.service.LaboratoryService;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.model.Error;
import xyz.jasenon.lab.common.model.base.Laboratory;
import xyz.jasenon.lab.common.util.Pair;

import java.util.List;

@DubboService
public class LaboratoryServiceImpl extends ServiceImpl<LaboratoryMapper, Laboratory> implements LaboratoryService {

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
        Error error = laboratory.validate();
        if (error.error()){
            String errorStr = String.join(",", error.errors());
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), errorStr);
        }
        save(laboratory);
        return laboratory;
    }

    @Override
    public Laboratory update(String laboratoryId, Laboratory laboratory) {
        var ctx = Holder.get();
        if (ctx == null){
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "登陆已过期");
        }
        if (ctx.canViewLaboratory(laboratoryId)){
            throw new BusinessException(HttpStatus.FORBIDDEN.value(), "你无权修改此实验室");
        }
        Error error = laboratory.validate();
        if (error.error()){
            String errorStr = String.join(",", error.errors());
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), errorStr);
        }
        laboratory.setId(laboratoryId);
        updateById(laboratory);
        return laboratory;
    }

    @Override
    public void delete(String laboratoryId) {
        var ctx = Holder.get();
        if (ctx == null){
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "登陆已过期");
        }
        if (ctx.canViewLaboratory(laboratoryId)){
            throw new BusinessException(HttpStatus.FORBIDDEN.value(), "你无权删除此实验室");
        }
        removeById(laboratoryId);
    }
}
