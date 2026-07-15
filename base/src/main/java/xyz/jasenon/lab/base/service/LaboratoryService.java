package xyz.jasenon.lab.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.jasenon.lab.common.model.base.Laboratory;
import xyz.jasenon.lab.common.util.Pair;

import java.util.List;

public interface LaboratoryService extends IService<Laboratory> {

    List<Pair<String,String>> collectionOrgName();

    List<Pair<String,String>> collectionBuildingName();

    Laboratory create(Laboratory laboratory);

    Laboratory update(String laboratoryId, Laboratory laboratory);

    void delete(String laboratoryId);

}
