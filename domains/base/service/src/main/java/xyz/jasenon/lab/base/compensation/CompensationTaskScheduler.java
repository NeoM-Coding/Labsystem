package xyz.jasenon.lab.base.compensation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.base.api.model.CompensationTask;
import xyz.jasenon.lab.base.api.model.CompensationTaskLog;
import xyz.jasenon.lab.base.mapper.CompensationTaskLogMapper;
import xyz.jasenon.lab.base.mapper.CompensationTaskMapper;
import java.net.InetAddress;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CompensationTaskScheduler {
    private final CompensationTaskMapper taskMapper;
    private final CompensationTaskLogMapper logMapper;
    private final Map<String, CompensationTaskHandler> handlers;
    private final String workerId;

    public CompensationTaskScheduler(CompensationTaskMapper taskMapper, CompensationTaskLogMapper logMapper,
                                     List<CompensationTaskHandler> handlers) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                CompensationTaskHandler::type, Function.identity()));
        this.workerId = host() + ":" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${lab.compensation.poll-delay-ms:30000}",
            initialDelayString = "${lab.compensation.initial-delay-ms:30000}")
    public void poll() {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.findDue(now).forEach(task -> {
            if (taskMapper.claim(task.getId(), workerId, now, now.plusMinutes(10)) == 1) execute(task, now);
        });
    }

    void execute(CompensationTask task, LocalDateTime now) {
        LocalDateTime scheduledAt = task.getNextFireAt();
        LocalDateTime next = nextFire(task, now);
        boolean misfired = scheduledAt.isBefore(now.minusMinutes(5));
        if (misfired && task.getMisfirePolicy() == CompensationTask.MisfirePolicy.SKIP) {
            finishTask(task, scheduledAt, next, now, "SKIPPED", null);
            writeLog(task.getCode(), scheduledAt, now, "SKIPPED", 0, "misfire skipped");
            return;
        }
        CompensationTaskHandler handler = handlers.get(task.getHandlerType());
        if (handler == null) {
            finishTask(task, scheduledAt, next, now, "FAILED", "未注册处理器: " + task.getHandlerType());
            writeLog(task.getCode(), scheduledAt, now, "FAILED", 0, "handler missing");
            return;
        }
        try {
            CompensationResult result = handler.execute(task.getPayload() == null ? Map.of() : task.getPayload());
            finishTask(task, scheduledAt, next, now, "SUCCESS", null);
            writeLog(task.getCode(), scheduledAt, now, "SUCCESS", result.affectedCount(), result.message());
        } catch (Exception e) {
            String error = abbreviate(e.getMessage());
            finishTask(task, scheduledAt, next, now, "FAILED", error);
            writeLog(task.getCode(), scheduledAt, now, "FAILED", 0, error);
        }
    }

    private void finishTask(CompensationTask task, LocalDateTime scheduledAt, LocalDateTime next,
                            LocalDateTime now, String status, String error) {
        task.setLastScheduledAt(scheduledAt); task.setLastStartedAt(now); task.setLastFinishedAt(LocalDateTime.now());
        task.setLastStatus(status); task.setLastError(error); task.setNextFireAt(next);
        task.setLockedBy(null); task.setLockUntil(null); taskMapper.updateById(task);
    }

    private void writeLog(String code, LocalDateTime scheduled, LocalDateTime started,
                          String status, int count, String message) {
        CompensationTaskLog log = new CompensationTaskLog(); log.setTaskCode(code); log.setScheduledAt(scheduled);
        log.setStartedAt(started); log.setFinishedAt(LocalDateTime.now()); log.setStatus(status);
        log.setAffectedCount(count); log.setMessage(abbreviate(message)); logMapper.insert(log);
    }

    static LocalDateTime nextFire(CompensationTask task, LocalDateTime now) {
        ZoneId zone = ZoneId.of(task.getZoneId());
        ZonedDateTime next = CronExpression.parse(task.getCronExpression()).next(now.atZone(zone));
        if (next == null) throw new IllegalArgumentException("Cron 不存在下一次触发时间");
        return next.toLocalDateTime();
    }
    private static String abbreviate(String value) { return value == null ? null : value.substring(0, Math.min(2000, value.length())); }
    private static String host() { try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "unknown"; } }
}
