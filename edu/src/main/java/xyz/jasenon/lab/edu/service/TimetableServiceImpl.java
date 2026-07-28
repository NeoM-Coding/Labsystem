package xyz.jasenon.lab.edu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.edu.api.TimetableService;
import xyz.jasenon.lab.edu.api.command.TimetableClear;
import xyz.jasenon.lab.edu.api.command.TimetableCreate;
import xyz.jasenon.lab.edu.api.command.TimetableDelete;
import xyz.jasenon.lab.edu.api.command.TimetableImport;
import xyz.jasenon.lab.edu.api.command.TimetableListQuery;
import xyz.jasenon.lab.edu.api.command.TimetableUpdate;
import xyz.jasenon.lab.edu.api.model.WeekType;
import xyz.jasenon.lab.edu.api.view.TimetableImportError;
import xyz.jasenon.lab.edu.api.view.TimetableImportResult;
import xyz.jasenon.lab.edu.api.view.TimetableView;
import xyz.jasenon.lab.edu.mapper.SemesterMapper;
import xyz.jasenon.lab.edu.mapper.TimetableMapper;
import xyz.jasenon.lab.edu.model.Semester;
import xyz.jasenon.lab.edu.model.Timetable;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DubboService
@Traced("timetable-service")
public class TimetableServiceImpl extends ServiceImpl<TimetableMapper, Timetable> implements TimetableService {

    private static final Logger log = LoggerFactory.getLogger(TimetableServiceImpl.class);
    private static final Pattern ITEM_PATTERN = Pattern.compile("^([^<>]+)<>([^<>]+)<>([^<>]+)$");
    private static final Pattern WEEK_PATTERN = Pattern.compile(
            "^(\\d+)-(\\d+)周(?:\\((单|双)\\))?\\[(.+)]$"
    );
    private static final Pattern SECTION_PATTERN = Pattern.compile("^(\\d+)-(\\d+)节$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})$");
    private static final List<SectionInfo> SECTIONS = List.of(
            new SectionInfo(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            new SectionInfo(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
            new SectionInfo(3, LocalTime.of(10, 0), LocalTime.of(10, 45)),
            new SectionInfo(4, LocalTime.of(10, 55), LocalTime.of(11, 40)),
            new SectionInfo(5, LocalTime.of(14, 10), LocalTime.of(14, 55)),
            new SectionInfo(6, LocalTime.of(15, 5), LocalTime.of(15, 50)),
            new SectionInfo(7, LocalTime.of(16, 0), LocalTime.of(16, 45)),
            new SectionInfo(8, LocalTime.of(16, 55), LocalTime.of(17, 40)),
            new SectionInfo(9, LocalTime.of(18, 40), LocalTime.of(19, 25)),
            new SectionInfo(10, LocalTime.of(19, 30), LocalTime.of(20, 15)),
            new SectionInfo(11, LocalTime.of(20, 20), LocalTime.of(21, 5))
    );

    private final SemesterMapper semesterMapper;
    private final TransactionTemplate transactionTemplate;
    private final int importMaxBytes;

    public TimetableServiceImpl(
            SemesterMapper semesterMapper,
            TransactionTemplate transactionTemplate,
            @Value("${lab.edu.import-max-bytes:5242880}") int importMaxBytes
    ) {
        this.semesterMapper = semesterMapper;
        this.transactionTemplate = transactionTemplate;
        this.importMaxBytes = importMaxBytes;
    }

    @Override
    @ActionAuthorized
    public RpcResult<List<TimetableView>> list(TimetableListQuery query) {
        requireText(query.semesterId(), "学期ID不能为空");
        if (semesterMapper.selectById(query.semesterId().trim()) == null) {
            throw new BusinessException(404, "学期不存在");
        }
        UserContext context = requireContext();
        Set<String> visible = Set.copyOf(context.filterLaboratoryIds());
        List<String> requested = normalizeIds(query.laboratoryIds());
        List<String> effective = requested.isEmpty()
                ? visible.stream().sorted().toList()
                : requested.stream().filter(visible::contains).toList();
        if (effective.isEmpty()) {
            return RpcResult.success(List.of());
        }
        Map<String, String> laboratoryNames = laboratoryNames(context);
        List<TimetableView> result = lambdaQuery()
                .eq(Timetable::getSemesterId, query.semesterId().trim())
                .in(Timetable::getLaboratoryId, effective)
                .orderByAsc(Timetable::getLaboratoryId)
                .orderByAsc(Timetable::getWeekday)
                .orderByAsc(Timetable::getStartTime)
                .list()
                .stream()
                .map(timetable -> view(timetable, laboratoryNames.get(timetable.getLaboratoryId())))
                .toList();
        return RpcResult.success(result);
    }

    @Override
    @ActionAuthorized
    @Audited("timetable.create")
    @Transactional
    public RpcResult<TimetableView> create(TimetableCreate command) {
        requireVisibleLaboratory(command.laboratoryId());
        Timetable timetable = createLocked(command, null);
        return RpcResult.success(view(timetable, currentLaboratoryName(timetable.getLaboratoryId())));
    }

    @Override
    @ActionAuthorized
    @Audited("timetable.update")
    @Transactional
    public RpcResult<TimetableView> update(TimetableUpdate command) {
        requireText(command.timetableId(), "课表ID不能为空");
        Timetable existing = getById(command.timetableId().trim());
        if (existing == null) {
            throw new BusinessException(404, "课表不存在");
        }
        requireVisibleLaboratory(existing.getLaboratoryId());
        requireVisibleLaboratory(command.laboratoryId());
        Timetable replacement = createEntity(
                command.semesterId(), command.laboratoryId(), command.courseName(), command.teacherName(),
                command.weekType(), command.startWeek(), command.endWeek(),
                command.startTime(), command.endTime(), command.weekday()
        );
        Semester semester = lockSemester(replacement.getSemesterId());
        replacement.setId(existing.getId());
        replacement.setCreateAt(existing.getCreateAt());
        replacement.setUpdateAt(LocalDateTime.now());
        replacement.setSemesterInfo(new Semester(semester));
        requireNoConflict(replacement, existing.getId());
        updateById(replacement);
        return RpcResult.success(view(replacement, currentLaboratoryName(replacement.getLaboratoryId())));
    }

    @Override
    @ActionAuthorized
    @Audited("timetable.delete")
    @Transactional
    public RpcResult<Void> delete(TimetableDelete command) {
        requireText(command.timetableId(), "课表ID不能为空");
        Timetable timetable = getById(command.timetableId().trim());
        if (timetable == null) {
            throw new BusinessException(404, "课表不存在");
        }
        requireVisibleLaboratory(timetable.getLaboratoryId());
        lockSemester(timetable.getSemesterId());
        removeById(timetable.getId());
        return RpcResult.success();
    }

    @Override
    @ActionAuthorized
    @Audited("timetable.clear")
    @Transactional
    public RpcResult<Void> clear(TimetableClear command) {
        requireText(command.semesterId(), "学期ID不能为空");
        requireVisibleLaboratory(command.laboratoryId());
        lockSemester(command.semesterId().trim());
        lambdaUpdate()
                .eq(Timetable::getSemesterId, command.semesterId().trim())
                .eq(Timetable::getLaboratoryId, command.laboratoryId().trim())
                .remove();
        return RpcResult.success();
    }

    @Override
    @ActionAuthorized
    @Audited("timetable.import")
    public RpcResult<TimetableImportResult> importExcel(TimetableImport command) {
        requireText(command.semesterId(), "学期ID不能为空");
        requireVisibleLaboratory(command.laboratoryId());
        validateFile(command.filename(), command.content());

        ParsedWorkbook workbook = parseWorkbook(command.content());
        List<TimetableImportError> errors = new ArrayList<>(workbook.errors());
        int success = 0;
        for (ParsedEntry entry : workbook.entries()) {
            String failure = transactionTemplate.execute(status -> {
                try {
                    TimetableCreate create = new TimetableCreate(
                            command.semesterId(), command.laboratoryId(), entry.courseName(), entry.teacherName(),
                            entry.weekType(), entry.startWeek(), entry.endWeek(),
                            entry.startTime(), entry.endTime(), entry.weekday()
                    );
                    createLocked(create, null);
                    return null;
                } catch (BusinessException exception) {
                    status.setRollbackOnly();
                    return exception.getMessage();
                } catch (RuntimeException exception) {
                    status.setRollbackOnly();
                    log.error("Excel timetable item import failed row={} column={}",
                            entry.rowIndex(), entry.columnIndex(), exception);
                    return "课表写入失败";
                }
            });
            if (failure == null) {
                success++;
            } else {
                errors.add(new TimetableImportError(
                        entry.rowIndex(), entry.columnIndex(), entry.rawContent(), failure
                ));
            }
        }
        errors.sort(Comparator.comparing(TimetableImportError::rowIndex)
                .thenComparing(TimetableImportError::columnIndex));
        return RpcResult.success(new TimetableImportResult(success, errors.size(), errors));
    }

    private Timetable createLocked(TimetableCreate command, String excludedId) {
        Timetable timetable = createEntity(
                command.semesterId(), command.laboratoryId(), command.courseName(), command.teacherName(),
                command.weekType(), command.startWeek(), command.endWeek(),
                command.startTime(), command.endTime(), command.weekday()
        );
        Semester semester = lockSemester(timetable.getSemesterId());
        timetable.setSemesterInfo(new Semester(semester));
        timetable.setCreateAt(LocalDateTime.now());
        timetable.setUpdateAt(timetable.getCreateAt());
        requireNoConflict(timetable, excludedId);
        save(timetable);
        return timetable;
    }

    private Timetable createEntity(
            String semesterId,
            String laboratoryId,
            String courseName,
            String teacherName,
            WeekType weekType,
            Integer startWeek,
            Integer endWeek,
            LocalTime startTime,
            LocalTime endTime,
            Integer weekday
    ) {
        requireText(semesterId, "学期ID不能为空");
        requireText(laboratoryId, "实验室ID不能为空");
        requireText(courseName, "课程名称不能为空");
        requireText(teacherName, "教师名称不能为空");
        if (weekType == null) throw new BusinessException(400, "周次类型不能为空");
        if (startWeek == null || endWeek == null || startWeek < 1 || endWeek < startWeek) {
            throw new BusinessException(400, "周次范围不合法");
        }
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(400, "上课开始时间必须早于结束时间");
        }
        if (weekday == null || weekday < 1 || weekday > 7) {
            throw new BusinessException(400, "weekday 必须在 1 到 7 之间");
        }
        Timetable timetable = new Timetable();
        timetable.setSemesterId(semesterId.trim());
        timetable.setLaboratoryId(laboratoryId.trim());
        timetable.setCourseName(courseName.trim());
        timetable.setTeacherName(teacherName.trim());
        timetable.setWeekType(weekType);
        timetable.setStartWeek(startWeek);
        timetable.setEndWeek(endWeek);
        timetable.setStartTime(startTime);
        timetable.setEndTime(endTime);
        timetable.setWeekday(weekday);
        return timetable;
    }

    private Semester lockSemester(String semesterId) {
        Semester semester = semesterMapper.selectByIdForUpdate(semesterId);
        if (semester == null) {
            throw new BusinessException(404, "学期不存在");
        }
        return semester;
    }

    private void requireNoConflict(Timetable candidate, String excludedId) {
        List<Timetable> possible = lambdaQuery()
                .eq(Timetable::getSemesterId, candidate.getSemesterId())
                .eq(Timetable::getWeekday, candidate.getWeekday())
                .list();
        for (Timetable existing : possible) {
            if (Objects.equals(existing.getId(), excludedId)) continue;
            if (hasConflict(candidate, existing)) {
                throw new BusinessException(409, "课程安排冲突，冲突课表ID：" + existing.getId());
            }
        }
    }

    static boolean hasConflict(Timetable first, Timetable second) {
        boolean sameLaboratory = Objects.equals(first.getLaboratoryId(), second.getLaboratoryId());
        boolean sameTeacher = normalizeName(first.getTeacherName())
                .equals(normalizeName(second.getTeacherName()));
        if (!sameLaboratory && !sameTeacher) return false;
        if (!Objects.equals(first.getWeekday(), second.getWeekday())) return false;
        int intersectionStart = Math.max(first.getStartWeek(), second.getStartWeek());
        int intersectionEnd = Math.min(first.getEndWeek(), second.getEndWeek());
        if (intersectionStart > intersectionEnd) return false;
        if (!weekTypeOverlap(first.getWeekType(), second.getWeekType(), intersectionStart, intersectionEnd)) {
            return false;
        }
        return first.getStartTime().isBefore(second.getEndTime())
                && second.getStartTime().isBefore(first.getEndTime());
    }

    private static boolean weekTypeOverlap(WeekType first, WeekType second, int start, int end) {
        if (first == WeekType.Both || second == WeekType.Both) return true;
        if (first != second) return false;
        int wantedParity = first == WeekType.Single ? 1 : 0;
        int firstMatchingWeek = start % 2 == wantedParity ? start : start + 1;
        return firstMatchingWeek <= end;
    }

    private ParsedWorkbook parseWorkbook(byte[] content) {
        List<ParsedEntry> entries = new ArrayList<>();
        List<TimetableImportError> errors = new ArrayList<>();
        try {
            FesodSheet.read(new ByteArrayInputStream(content), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext context) {
                    int rowIndex = context.readRowHolder().getRowIndex();
                    row.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(cell -> parseCell(rowIndex, cell.getKey(), cell.getValue(), entries, errors));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().doRead();
        } catch (RuntimeException exception) {
            throw new BusinessException(400, "Excel 文件无法解析");
        }
        entries.sort(Comparator.comparing(ParsedEntry::rowIndex).thenComparing(ParsedEntry::columnIndex));
        errors.sort(Comparator.comparing(TimetableImportError::rowIndex)
                .thenComparing(TimetableImportError::columnIndex));
        return new ParsedWorkbook(entries, errors);
    }

    private static void parseCell(
            int rowIndex,
            int columnIndex,
            String cellValue,
            List<ParsedEntry> entries,
            List<TimetableImportError> errors
    ) {
        if (cellValue == null || cellValue.isBlank()) return;
        String washed = cellValue.replace("：", ":").replace("\r", "").replace("\n", "");
        for (String rawItem : washed.split(",")) {
            String raw = rawItem.trim();
            if (raw.isEmpty()) continue;
            try {
                entries.add(parseItem(rowIndex, columnIndex, raw));
            } catch (BusinessException exception) {
                errors.add(new TimetableImportError(rowIndex, columnIndex, raw, exception.getMessage()));
            }
        }
    }

    private static ParsedEntry parseItem(int rowIndex, int columnIndex, String raw) {
        Matcher item = ITEM_PATTERN.matcher(raw);
        if (!item.matches()) {
            throw new BusinessException(400, "格式应为：课程名称<>时间信息<>教师名称");
        }
        String course = item.group(1).trim();
        String time = item.group(2).trim();
        String teacher = item.group(3).trim();
        if (course.isEmpty() || teacher.isEmpty()) {
            throw new BusinessException(400, "课程名称和教师名称不能为空");
        }
        Matcher week = WEEK_PATTERN.matcher(time);
        if (!week.matches()) {
            throw new BusinessException(400, "时间格式应为 X-Y周[节次或时间]");
        }
        int startWeek;
        int endWeek;
        try {
            startWeek = Integer.parseInt(week.group(1));
            endWeek = Integer.parseInt(week.group(2));
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "周次数字不合法");
        }
        if (startWeek < 1 || endWeek < startWeek) {
            throw new BusinessException(400, "周次范围不合法");
        }
        WeekType weekType = switch (week.group(3) == null ? "" : week.group(3)) {
            case "单" -> WeekType.Single;
            case "双" -> WeekType.Double;
            default -> WeekType.Both;
        };
        TimeRange range = parseTimeRange(week.group(4).trim());
        return new ParsedEntry(
                rowIndex, columnIndex, raw, course, teacher, weekType,
                startWeek, endWeek, range.start(), range.end(), columnIndex + 1
        );
    }

    private static TimeRange parseTimeRange(String detail) {
        Matcher section = SECTION_PATTERN.matcher(detail);
        if (section.matches()) {
            int start = Integer.parseInt(section.group(1));
            int end = Integer.parseInt(section.group(2));
            if (start < 1 || end < start || end > SECTIONS.size()) {
                throw new BusinessException(400, "节次范围必须在 1 到 " + SECTIONS.size() + " 之间");
            }
            return new TimeRange(SECTIONS.get(start - 1).start(), SECTIONS.get(end - 1).end());
        }
        Matcher time = TIME_PATTERN.matcher(detail);
        if (time.matches()) {
            try {
                LocalTime start = LocalTime.of(Integer.parseInt(time.group(1)), Integer.parseInt(time.group(2)));
                LocalTime end = LocalTime.of(Integer.parseInt(time.group(3)), Integer.parseInt(time.group(4)));
                if (!start.isBefore(end)) throw new BusinessException(400, "开始时间必须早于结束时间");
                return new TimeRange(start, end);
            } catch (java.time.DateTimeException exception) {
                throw new BusinessException(400, "时间值不合法");
            }
        }
        throw new BusinessException(400, "时间详情应为 X-Y节 或 HH:mm-HH:mm");
    }

    private void validateFile(String filename, byte[] content) {
        requireText(filename, "Excel 文件名不能为空");
        String lower = filename.trim().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new BusinessException(400, "仅支持 .xlsx 或 .xls 文件");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(400, "Excel 文件不能为空");
        }
        if (content.length > importMaxBytes) {
            throw new BusinessException(400, "Excel 文件不能超过 " + importMaxBytes + " 字节");
        }
        boolean xlsx = content.length >= 2 && content[0] == 'P' && content[1] == 'K';
        boolean xls = content.length >= 4
                && (content[0] & 0xff) == 0xD0 && (content[1] & 0xff) == 0xCF
                && (content[2] & 0xff) == 0x11 && (content[3] & 0xff) == 0xE0;
        if (!xlsx && !xls) {
            throw new BusinessException(400, "文件内容不是有效的 Excel 格式");
        }
    }

    private static TimetableView view(Timetable timetable, String laboratoryName) {
        return new TimetableView(
                timetable.getId(),
                timetable.getSemesterId(),
                SemesterServiceImpl.view(timetable.getSemesterInfo()),
                timetable.getLaboratoryId(),
                laboratoryName,
                timetable.getCourseName(),
                timetable.getTeacherName(),
                timetable.getWeekType(),
                timetable.getStartWeek(),
                timetable.getEndWeek(),
                timetable.getStartTime(),
                timetable.getEndTime(),
                timetable.getWeekday()
        );
    }

    private static UserContext requireContext() {
        UserContext context = UserContextHolder.get();
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()) {
            throw new BusinessException(401, "登陆已过期");
        }
        return context;
    }

    private static void requireVisibleLaboratory(String laboratoryId) {
        requireText(laboratoryId, "实验室ID不能为空");
        if (!requireContext().canViewLaboratory(laboratoryId.trim())) {
            throw new BusinessException(403, "无权访问该实验室");
        }
    }

    private static String currentLaboratoryName(String laboratoryId) {
        return laboratoryNames(requireContext()).get(laboratoryId);
    }

    private static Map<String, String> laboratoryNames(UserContext context) {
        Map<String, String> result = new HashMap<>();
        if (context.getLaboratoryScopes() != null) {
            context.getLaboratoryScopes().stream()
                    .filter(Objects::nonNull)
                    .filter(scope -> scope.getLaboratoryId() != null)
                    .forEach(scope -> result.put(scope.getLaboratoryId(), scope.getLaboratoryName()));
        }
        return result;
    }

    private static List<String> normalizeIds(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(400, message);
    }

    private record SectionInfo(int section, LocalTime start, LocalTime end) {
    }
    private record TimeRange(LocalTime start, LocalTime end) {
    }
    private record ParsedEntry(
            int rowIndex,
            int columnIndex,
            String rawContent,
            String courseName,
            String teacherName,
            WeekType weekType,
            int startWeek,
            int endWeek,
            LocalTime startTime,
            LocalTime endTime,
            int weekday
    ) {
    }
    private record ParsedWorkbook(List<ParsedEntry> entries, List<TimetableImportError> errors) {
    }
}
