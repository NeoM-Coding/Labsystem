package xyz.jasenon.lab.web.rule;

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
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/smart-strategies")
@Traced("smart-strategy-web")
public class SmartStrategyController {

    @DubboReference(check = false)
    private SmartStrategyService smartStrategyService;

    @GetMapping
    public DiyResponseEntity<R<List<RuntimeRevision>>> list() {
        return DiyResponseEntity.of(R.success(
                smartStrategyService.list(new SmartStrategyListQuery())
        ));
    }

    @GetMapping("/{runtimeId}")
    public DiyResponseEntity<R<RuntimeRevision>> get(@PathVariable String runtimeId) {
        return DiyResponseEntity.of(R.success(
                smartStrategyService.get(new SmartStrategyGet(runtimeId))
        ));
    }

    @PostMapping
    public DiyResponseEntity<R<RuntimeRevision>> create(@RequestBody RuntimeRevision revision) {
        return DiyResponseEntity.of(R.success(
                smartStrategyService.create(new SmartStrategyCreate(revision))
        ));
    }

    @PutMapping("/{runtimeId}")
    public DiyResponseEntity<R<RuntimeRevision>> update(
            @PathVariable String runtimeId,
            @RequestBody RuntimeRevision revision) {
        // path ID 是资源身份，规则服务会再次校验它与 revision.runtimeId 一致。
        return DiyResponseEntity.of(R.success(
                smartStrategyService.update(new SmartStrategyUpdate(runtimeId, revision))
        ));
    }

    @DeleteMapping("/{runtimeId}")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String runtimeId) {
        smartStrategyService.delete(new SmartStrategyDelete(runtimeId));
        return DiyResponseEntity.of(R.success());
    }

    @PutMapping("/{runtimeId}/enabled")
    public DiyResponseEntity<R<RuntimeRevision>> enable(@PathVariable String runtimeId) {
        return DiyResponseEntity.of(R.success(
                smartStrategyService.changeStatus(new SmartStrategyStatusChange(runtimeId, true))
        ));
    }

    @DeleteMapping("/{runtimeId}/enabled")
    public DiyResponseEntity<R<RuntimeRevision>> disable(@PathVariable String runtimeId) {
        return DiyResponseEntity.of(R.success(
                smartStrategyService.changeStatus(new SmartStrategyStatusChange(runtimeId, false))
        ));
    }
}
