package xyz.jasenon.lab.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.jasenon.lab.common.model.base.Laboratory;
import xyz.jasenon.lab.common.util.Pair;

import java.util.List;
import java.util.Set;

@Mapper
public interface LaboratoryMapper extends BaseMapper<Laboratory> {

    List<Pair<String,String>> collectionOrgName();

    List<Pair<String,String>> collectionBuildingName();

}
