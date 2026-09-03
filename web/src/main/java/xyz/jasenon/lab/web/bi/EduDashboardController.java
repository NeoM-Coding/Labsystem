package xyz.jasenon.lab.web.bi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.bi.api.EduDashboardService;
import xyz.jasenon.lab.bi.api.model.EduTimeRange;
import xyz.jasenon.lab.bi.api.query.EduDashboardQuery;
import xyz.jasenon.lab.bi.api.view.EduDashboardView;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/bi/edu")
@Traced("edu-dashboard-web")
@Tag(name = "教务数据分析", description = "在当前用户可见实验室范围内提供 ECharts 所需的课程与学时统计")
public class EduDashboardController {

    @DubboReference(check = false)
    private EduDashboardService eduDashboardService;

    @GetMapping("/dashboard")
    @Operation(
            summary = "查询教务看板",
            description = """
                    range 支持 TODAY、CURRENT_MONTH、CURRENT_SEMESTER。
                    分别按开课时间、日期、学期周次返回时间序列，同时返回课程状态、学时、星期、节次分布和正在进行课程。
                    laboratoryIds 可选，并会与当前会话可见实验室范围求交集。需要 edu_data_analysis 权限。
                    所有状态基于响应 calculatedAt 同一时刻，以 [startAt, endAt) 计算。
                    """
    )
    public DiyResponseEntity<R<EduDashboardView>> dashboard(
            @Parameter(description = "统计时间维度", example = "CURRENT_SEMESTER")
            @RequestParam EduTimeRange range,
            @Parameter(description = "可选实验室ID；只能收窄当前用户可见范围")
            @RequestParam(required = false) List<String> laboratoryIds
    ) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> eduDashboardService.dashboard(
                        new EduDashboardQuery(range, laboratoryIds)
                ))
        ));
    }
}
