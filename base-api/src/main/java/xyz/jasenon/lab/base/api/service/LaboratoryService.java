package xyz.jasenon.lab.base.api.service;

import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.vo.LaboratoryVO;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

public interface LaboratoryService {

    RpcResult<List<Pair<String,String>>> collectionOrgName();

    RpcResult<List<Pair<String,String>>> collectionBuildingName();

    RpcResult<List<LaboratoryVO>> list(String[] buildingNames, String[] orgNames);

    RpcResult<Laboratory> create(LaboratoryCreate command);

    RpcResult<Laboratory> update(LaboratoryEdit command);

    RpcResult<Void> delete(LaboratoryDelete command);

}
