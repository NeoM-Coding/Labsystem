package xyz.jasenon.lab.edu.api;

import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.edu.api.command.SemesterCreate;
import xyz.jasenon.lab.edu.api.command.SemesterDelete;
import xyz.jasenon.lab.edu.api.command.SemesterListQuery;
import xyz.jasenon.lab.edu.api.command.SemesterUpdate;
import xyz.jasenon.lab.edu.api.view.SemesterView;

import java.util.List;

public interface SemesterService {

    RpcResult<List<SemesterView>> list(SemesterListQuery query);

    RpcResult<SemesterView> create(SemesterCreate command);

    RpcResult<SemesterView> update(SemesterUpdate command);

    RpcResult<Void> delete(SemesterDelete command);
}
