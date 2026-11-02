package xyz.jasenon.lab.web.edu;

import xyz.jasenon.lab.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.edu.api.TimetableService;
import xyz.jasenon.lab.edu.api.command.TimetableClear;
import xyz.jasenon.lab.edu.api.command.TimetableCreate;
import xyz.jasenon.lab.edu.api.command.TimetableDelete;
import xyz.jasenon.lab.edu.api.command.TimetableImport;
import xyz.jasenon.lab.edu.api.command.TimetableImportTemplateQuery;
import xyz.jasenon.lab.edu.api.command.TimetableExportQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import xyz.jasenon.lab.edu.api.command.TimetableListQuery;
import xyz.jasenon.lab.edu.api.command.TimetableUpdate;
import xyz.jasenon.lab.edu.api.view.TimetableImportResult;
import xyz.jasenon.lab.edu.api.view.TimetableView;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/edu/timetables")
@Traced("timetable-web")
@Tag(name = "课表管理", description = "管理当前用户可见实验室的排课、冲突校验和 Excel 导入")
public class TimetableController {

    @DubboReference(check = false, timeout = 30000)
    private TimetableService timetableService;

    private final int importMaxBytes;

    public TimetableController(@Value("${lab.edu.import-max-bytes:5242880}") int importMaxBytes) {
        this.importMaxBytes = importMaxBytes;
    }

    @GetMapping
    @Operation(summary = "查询课表", description = "学期必填；请求的实验室范围会与当前会话可见实验室取交集。")
    public DiyResponseEntity<R<List<TimetableView>>> list(
            @RequestParam String semesterId,
            @RequestParam(required = false) List<String> laboratoryIds
    ) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> timetableService.list(
                        new TimetableListQuery(semesterId, laboratoryIds)
                ))
        ));
    }

    @PostMapping
    @Operation(
            summary = "创建排课",
            description = "同学期内，同实验室或同教师在相同星期、有效周次及重叠时间内不能重复排课。"
    )
    public DiyResponseEntity<R<TimetableView>> create(@RequestBody TimetableCreate command) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> timetableService.create(command))
        ));
    }

    @PutMapping("/{timetableId}")
    @Operation(summary = "修改排课", description = "全量修改课表，并在排除自身记录后重新执行冲突检查。")
    public DiyResponseEntity<R<TimetableView>> update(
            @PathVariable String timetableId,
            @RequestBody TimetableUpdate command
    ) {
        TimetableUpdate downstream = new TimetableUpdate(
                timetableId,
                command.semesterId(),
                command.laboratoryId(),
                command.courseName(),
                command.teacherName(),
                command.weekType(),
                command.startWeek(),
                command.endWeek(),
                command.startSection(),
                command.endSection(),
                command.startTime(),
                command.endTime(),
                command.weekday()
        );
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> timetableService.update(downstream))
        ));
    }

    @DeleteMapping("/{timetableId}")
    @Operation(summary = "删除排课", description = "删除一条当前用户有权访问的实验室课表。")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String timetableId) {
        RpcClient.run(() -> timetableService.delete(new TimetableDelete(timetableId)));
        return DiyResponseEntity.of(R.success());
    }

    @DeleteMapping
    @Operation(summary = "清空实验室课表", description = "清空指定学期、指定实验室的全部课表记录。")
    public DiyResponseEntity<R<Void>> clear(
            @RequestParam String semesterId,
            @RequestParam String laboratoryId
    ) {
        RpcClient.run(() -> timetableService.clear(new TimetableClear(semesterId, laboratoryId)));
        return DiyResponseEntity.of(R.success());
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载课表 Excel 模板", description = "第一张表为无表头的课表数据，A-G 对应周一至周日；第二张表为填写说明。需要课表管理权限。")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] content = RpcClient.call(() -> timetableService.importTemplate(
                new TimetableImportTemplateQuery()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"timetable-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/export")
    @Operation(summary = "导出可打印的 Excel 课表", description = "学期和实验室必填，每个实验室一张工作表，A3 横向单页打印。包含全部课程，不受前端搜索词影响；需要课表查看权限和实验室可见权限。")
    public ResponseEntity<byte[]> exportExcel(@RequestParam String semesterId,
                                              @RequestParam List<String> laboratoryIds) {
        byte[] content = RpcClient.call(() -> timetableService.exportExcel(
                new TimetableExportQuery(semesterId, laboratoryIds)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timetable-print.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @Operation(
            summary = "从 Excel 导入课表",
            description = """
                    使用 semesterId 和 laboratoryId 确定归属。单元格格式为
                    课程名称<>时间信息<>教师名称；只读取第一张工作表，无表头，A-G 列代表周一至周日。
                    每条课表独立提交，响应包含成功数、失败数和错误位置。
                    """
    )
    public DiyResponseEntity<R<TimetableImportResult>> importExcel(
            @Parameter(description = "仅支持 .xlsx 或 .xls，最大 5 MiB")
            @RequestPart("excel") MultipartFile excel,
            @RequestParam String semesterId,
            @RequestParam String laboratoryId
    ) {
        if (excel.isEmpty()) {
            throw new BusinessException(ErrorCode.TIMETABLE_IMPORT_EMPTY);
        }
        if (excel.getSize() > importMaxBytes) {
            throw new BusinessException(ErrorCode.TIMETABLE_IMPORT_TOO_LARGE, "Excel 文件不能超过 " + importMaxBytes + " 字节");
        }
        byte[] content;
        try {
            content = excel.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.TIMETABLE_IMPORT_PARSE_FAILED, "Excel 文件读取失败");
        }
        TimetableImport command = new TimetableImport(
                semesterId, laboratoryId, excel.getOriginalFilename(), content
        );
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> timetableService.importExcel(command))
        ));
    }
}
