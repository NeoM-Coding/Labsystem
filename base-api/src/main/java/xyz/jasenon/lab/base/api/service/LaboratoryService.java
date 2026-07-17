package xyz.jasenon.lab.base.api.service;

import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.common.util.Pair;

import java.util.List;

public interface LaboratoryService {

    List<Pair<String,String>> collectionOrgName();

    List<Pair<String,String>> collectionBuildingName();

    Laboratory create(Laboratory laboratory);

    Laboratory update(String laboratoryId, Laboratory laboratory);

    void delete(String laboratoryId);

}
