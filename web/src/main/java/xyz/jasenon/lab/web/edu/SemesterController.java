package xyz.jasenon.lab.web.edu;

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
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.edu.api.SemesterService;
import xyz.jasenon.lab.edu.api.command.SemesterCreate;
import xyz.jasenon.lab.edu.api.command.SemesterDelete;
import xyz.jasenon.lab.edu.api.command.SemesterListQuery;
import xyz.jasenon.lab.edu.api.command.SemesterUpdate;
import xyz.jasenon.lab.edu.api.view.SemesterView;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/edu/semesters")
@Traced("semester-web")
@Tag(name = "学期管理", description = "维护学期日期范围以及供课表使用的学期信息")
public class SemesterController {

    @DubboReference(check = false)
    private SemesterService semesterService;

    @GetMapping
    @Operation(summary = "查询学期", description = "按名称关键字查询学期，结果按开始日期倒序排列。")
    public DiyResponseEntity<R<List<SemesterView>>> list(
            @RequestParam(required = false) String keyword
    ) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> semesterService.list(new SemesterListQuery(keyword)))
        ));
    }

    @PostMapping
    @Operation(summary = "创建学期", description = "学期名称格式必须为 YYYY-YYYY 第N学期，开始日期必须早于结束日期。")
    public DiyResponseEntity<R<SemesterView>> create(@RequestBody SemesterCreate command) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> semesterService.create(command))
        ));
    }

    @PutMapping("/{semesterId}")
    @Operation(summary = "修改学期", description = "修改学期后会同步刷新已有课表中的学期冗余快照。")
    public DiyResponseEntity<R<SemesterView>> update(
            @PathVariable String semesterId,
            @RequestBody SemesterUpdate command
    ) {
        SemesterUpdate downstream = new SemesterUpdate(
                semesterId, command.name(), command.startDate(), command.endDate()
        );
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> semesterService.update(downstream))
        ));
    }

    @DeleteMapping("/{semesterId}")
    @Operation(summary = "删除学期", description = "学期存在课表时拒绝删除，必须先清理相关课表。")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String semesterId) {
        RpcClient.run(() -> semesterService.delete(new SemesterDelete(semesterId)));
        return DiyResponseEntity.of(R.success());
    }
}
