package xyz.jasenon.lab.web.rule;

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
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.engine.api.SmartStrategyService;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyDelete;
import xyz.jasenon.lab.engine.api.command.SmartStrategyGet;
import xyz.jasenon.lab.engine.api.command.SmartStrategyListQuery;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.api.command.SmartStrategyUpdate;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/smart-strategies")
@Traced("smart-strategy-web")
@Tag(name = "智能策略", description = "管理规则引擎中的智能控制策略和运行状态")
public class SmartStrategyController {

    @DubboReference(check = false)
    private SmartStrategyService smartStrategyService;

    @GetMapping
    @Operation(summary = "查询智能策略", description = "返回当前用户有权查看的智能策略版本列表。")
    public DiyResponseEntity<R<List<RuntimeRevision>>> list() {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> smartStrategyService.list(new SmartStrategyListQuery()))
        ));
    }

    @GetMapping("/{runtimeId}")
    @Operation(summary = "查询智能策略详情", description = "根据运行时 ID 查询智能策略的当前版本和配置。")
    public DiyResponseEntity<R<RuntimeRevision>> get(@PathVariable String runtimeId) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> smartStrategyService.get(new SmartStrategyGet(runtimeId)))
        ));
    }

    @PostMapping
    @Operation(summary = "创建智能策略", description = "创建新的规则运行时和首个智能策略版本。")
    public DiyResponseEntity<R<RuntimeRevision>> create(@RequestBody RuntimeRevision revision) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> smartStrategyService.create(new SmartStrategyCreate(revision)))
        ));
    }

    @PutMapping("/{runtimeId}")
    @Operation(summary = "修改智能策略", description = "为指定规则运行时保存新的智能策略版本并刷新运行时。")
    public DiyResponseEntity<R<RuntimeRevision>> update(
            @PathVariable String runtimeId,
            @RequestBody RuntimeRevision revision) {
        // path ID 是资源身份，规则服务会再次校验它与 revision.runtimeId 一致。
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> smartStrategyService.update(new SmartStrategyUpdate(runtimeId, revision)))
        ));
    }

    @DeleteMapping("/{runtimeId}")
    @Operation(summary = "删除智能策略", description = "删除指定规则运行时及其持久化策略数据。")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String runtimeId) {
        RpcClient.run(() -> smartStrategyService.delete(new SmartStrategyDelete(runtimeId)));
        return DiyResponseEntity.of(R.success());
    }

    @PutMapping("/{runtimeId}/enabled")
    @Operation(summary = "启用智能策略", description = "启用指定规则运行时，使智能策略开始参与事件处理。")
    public DiyResponseEntity<R<RuntimeRevision>> enable(@PathVariable String runtimeId) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> smartStrategyService.changeStatus(
                        new SmartStrategyStatusChange(runtimeId, true)))
        ));
    }

    @DeleteMapping("/{runtimeId}/enabled")
    @Operation(summary = "停用智能策略", description = "停用指定规则运行时，同时保留其策略配置和版本数据。")
    public DiyResponseEntity<R<RuntimeRevision>> disable(@PathVariable String runtimeId) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> smartStrategyService.changeStatus(
                        new SmartStrategyStatusChange(runtimeId, false)))
        ));
    }
}
