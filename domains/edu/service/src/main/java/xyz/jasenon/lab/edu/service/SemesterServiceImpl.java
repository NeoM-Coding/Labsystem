package xyz.jasenon.lab.edu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.edu.api.SemesterService;
import xyz.jasenon.lab.edu.api.command.SemesterCreate;
import xyz.jasenon.lab.edu.api.command.SemesterDelete;
import xyz.jasenon.lab.edu.api.command.SemesterListQuery;
import xyz.jasenon.lab.edu.api.command.SemesterUpdate;
import xyz.jasenon.lab.edu.api.view.SemesterView;
import xyz.jasenon.lab.edu.mapper.SemesterMapper;
import xyz.jasenon.lab.edu.mapper.TimetableMapper;
import xyz.jasenon.lab.edu.model.Semester;
import xyz.jasenon.lab.edu.model.Timetable;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@DubboService
@Traced("semester-service")
public class SemesterServiceImpl extends ServiceImpl<SemesterMapper, Semester> implements SemesterService {

    private static final Pattern NAME_PATTERN = Pattern.compile("\\d{4}-\\d{4} 第\\d+学期");
    private final TimetableMapper timetableMapper;

    public SemesterServiceImpl(TimetableMapper timetableMapper) {
        this.timetableMapper = timetableMapper;
    }

    @Override
    @ActionAuthorized
    public RpcResult<List<SemesterView>> list(SemesterListQuery query) {
        String keyword = trimToNull(query.keyword());
        List<SemesterView> semesters = lambdaQuery()
                .like(keyword != null, Semester::getName, keyword)
                .orderByDesc(Semester::getStartDate)
                .orderByDesc(Semester::getCreateAt)
                .list()
                .stream()
                .map(SemesterServiceImpl::view)
                .toList();
        return RpcResult.success(semesters);
    }

    @Override
    @ActionAuthorized
    @Audited("semester.create")
    @Transactional
    public RpcResult<SemesterView> create(SemesterCreate command) {
        validate(command.name(), command.startDate(), command.endDate());
        String name = command.name().trim();
        requireNameAvailable(name, null);
        Semester semester = new Semester();
        semester.setName(name);
        semester.setStartDate(command.startDate());
        semester.setEndDate(command.endDate());
        semester.setCreateAt(LocalDateTime.now());
        semester.setUpdateAt(semester.getCreateAt());
        try {
            save(semester);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "学期名称已存在");
        }
        return RpcResult.success(view(semester));
    }

    @Override
    @ActionAuthorized
    @Audited("semester.update")
    @Transactional
    public RpcResult<SemesterView> update(SemesterUpdate command) {
        requireId(command.semesterId(), "学期ID不能为空");
        validate(command.name(), command.startDate(), command.endDate());
        Semester semester = baseMapper.selectByIdForUpdate(command.semesterId().trim());
        if (semester == null) {
            throw new BusinessException(404, "学期不存在");
        }
        String name = command.name().trim();
        requireNameAvailable(name, semester.getId());
        semester.setName(name);
        semester.setStartDate(command.startDate());
        semester.setEndDate(command.endDate());
        semester.setUpdateAt(LocalDateTime.now());
        try {
            updateById(semester);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "学期名称已存在");
        }

        // 冗余快照必须与学期主记录在同一事务内同步。
        List<Timetable> timetables = new com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<>(
                timetableMapper
        ).eq(Timetable::getSemesterId, semester.getId()).list();
        if (!timetables.isEmpty()) {
            Semester snapshot = new Semester(semester);
            timetables.forEach(timetable -> {
                timetable.setSemesterInfo(new Semester(snapshot));
                timetable.setUpdateAt(LocalDateTime.now());
            });
            for (Timetable timetable : timetables) {
                timetableMapper.updateById(timetable);
            }
        }
        return RpcResult.success(view(semester));
    }

    @Override
    @ActionAuthorized
    @Audited("semester.delete")
    @Transactional
    public RpcResult<Void> delete(SemesterDelete command) {
        requireId(command.semesterId(), "学期ID不能为空");
        Semester semester = baseMapper.selectByIdForUpdate(command.semesterId().trim());
        if (semester == null) {
            throw new BusinessException(404, "学期不存在");
        }
        Long count = new com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<>(
                timetableMapper
        ).eq(Timetable::getSemesterId, semester.getId()).count();
        if (count > 0) {
            throw new BusinessException(409, "该学期仍有课表记录，请先清理课表");
        }
        removeById(semester.getId());
        return RpcResult.success();
    }

    private void requireNameAvailable(String name, String excludedId) {
        long count = lambdaQuery()
                .eq(Semester::getName, name)
                .ne(excludedId != null, Semester::getId, excludedId)
                .count();
        if (count > 0) {
            throw new BusinessException(409, "学期名称已存在");
        }
    }

    static SemesterView view(Semester semester) {
        return new SemesterView(
                semester.getId(),
                semester.getName(),
                semester.getStartDate(),
                semester.getEndDate(),
                semester.getCreateAt(),
                semester.getUpdateAt()
        );
    }

    private static void validate(String name, LocalDate startDate, LocalDate endDate) {
        if (name == null || !NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new BusinessException(400, "学期名称格式应为 YYYY-YYYY 第N学期");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException(400, "学期开始日期和结束日期不能为空");
        }
        if (!startDate.isBefore(endDate)) {
            throw new BusinessException(400, "学期开始日期必须早于结束日期");
        }
    }

    private static void requireId(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, message);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
