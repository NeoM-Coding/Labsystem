package xyz.jasenon.lab.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.common.util.Pair;

import java.util.List;

@Mapper
public interface LaboratoryMapper extends BaseMapper<Laboratory> {

    String lockActiveLaboratory(@Param("laboratoryId") String laboratoryId);

    boolean hasActiveGateways(@Param("laboratoryId") String laboratoryId);

    boolean hasActiveDevices(@Param("laboratoryId") String laboratoryId);

    List<Pair<String,String>> collectionOrgName();

    List<Pair<String,String>> collectionBuildingName();

}
