package xyz.jasenon.lab.web.laboratory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.base.api.vo.LaboratoryVO;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/laboratories")
@Traced
@Tag(name = "实验室管理", description = "管理实验室资料以及当前用户可见的实验室范围")
public class LaboratoryController {

    @DubboReference(check = false)
    private LaboratoryService laboratoryService;

    @GetMapping
    @Operation(summary = "查询可见实验室", description = "根据当前用户的可见范围查询实验室，可按楼栋名称和所属单位名称筛选。")
    public DiyResponseEntity<R<List<LaboratoryVO>>> list(
            @RequestParam(required = false) String[] buildingNames,
            @RequestParam(required = false) String[] orgNames) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> laboratoryService.list(buildingNames, orgNames))
        ));
    }

    @GetMapping("/options/organizations")
    @Operation(summary = "查询所属单位选项", description = "返回当前可见实验室中可用于筛选的所属单位选项。")
    public DiyResponseEntity<R<List<Pair<String, String>>>> organizations() {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(laboratoryService::collectionOrgName)
        ));
    }

    @GetMapping("/options/buildings")
    @Operation(summary = "查询楼栋选项", description = "返回当前可见实验室中可用于筛选的楼栋选项。")
    public DiyResponseEntity<R<List<Pair<String, String>>>> buildings() {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(laboratoryService::collectionBuildingName)
        ));
    }

    @PostMapping
    @Operation(summary = "创建实验室", description = "创建实验室资料并同步初始化相关授权关系和用户可见范围。")
    public DiyResponseEntity<R<Laboratory>> create(@RequestBody LaboratoryCreate command) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> laboratoryService.create(command))
        ));
    }

    @PutMapping("/{laboratoryId}")
    @Operation(summary = "修改实验室", description = "根据实验室 ID 更新楼栋、所属单位、名称和管理人员等资料。")
    public DiyResponseEntity<R<Laboratory>> update(@PathVariable String laboratoryId,
                                                   @RequestBody LaboratoryEdit command) {
        // 使用 path ID 重新组装下游 Command，同时保留已有授权 Handler 的 DTO 契约。
        LaboratoryEdit downstream = new LaboratoryEdit(
                laboratoryId,
                command.buildingName(),
                command.orgName(),
                command.laboratoryName(),
                command.extra(),
                command.manager()
        );
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> laboratoryService.update(downstream))
        ));
    }

    @DeleteMapping("/{laboratoryId}")
    @Operation(summary = "删除实验室", description = "删除指定实验室，并清理其授权数据及相关用户的可见范围。")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String laboratoryId) {
        RpcClient.run(() -> laboratoryService.delete(new LaboratoryDelete(laboratoryId, null)));
        return DiyResponseEntity.of(R.success());
    }
}
