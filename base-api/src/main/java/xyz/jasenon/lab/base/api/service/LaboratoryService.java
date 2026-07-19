package xyz.jasenon.lab.base.api.service;

import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.vo.LaboratoryVO;
import xyz.jasenon.lab.common.util.Pair;

import java.util.List;

public interface LaboratoryService {

    List<Pair<String,String>> collectionOrgName();

    List<Pair<String,String>> collectionBuildingName();

    List<LaboratoryVO> list(String buildingName, String orgName);

    Laboratory create(LaboratoryCreate command);

    Laboratory update(LaboratoryEdit command);

    void delete(LaboratoryDelete command);

}
