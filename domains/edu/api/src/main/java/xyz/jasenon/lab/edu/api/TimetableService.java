package xyz.jasenon.lab.edu.api;

import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.edu.api.command.TimetableClear;
import xyz.jasenon.lab.edu.api.command.TimetableCreate;
import xyz.jasenon.lab.edu.api.command.TimetableDelete;
import xyz.jasenon.lab.edu.api.command.TimetableImport;
import xyz.jasenon.lab.edu.api.command.TimetableListQuery;
import xyz.jasenon.lab.edu.api.command.TimetableUpdate;
import xyz.jasenon.lab.edu.api.view.TimetableImportResult;
import xyz.jasenon.lab.edu.api.view.TimetableView;

import java.util.List;

public interface TimetableService {

    RpcResult<List<TimetableView>> list(TimetableListQuery query);

    RpcResult<TimetableView> create(TimetableCreate command);

    RpcResult<TimetableView> update(TimetableUpdate command);

    RpcResult<Void> delete(TimetableDelete command);

    RpcResult<Void> clear(TimetableClear command);

    RpcResult<TimetableImportResult> importExcel(TimetableImport command);
}
