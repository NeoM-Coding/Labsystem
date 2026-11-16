package xyz.jasenon.lab.bi.service;

import xyz.jasenon.lab.common.error.ErrorCode;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.jasenon.lab.bi.api.BiMaintenanceService;
import xyz.jasenon.lab.bi.telemetry.*;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import java.time.*;
import java.util.*;

@DubboService
public class BiMaintenanceServiceImpl implements BiMaintenanceService {
    private final JdbcTemplate jdbc;
    private final BiAdministrator admin;
    private final RecordArchive archive;
    public BiMaintenanceServiceImpl(JdbcTemplate jdbc,BiAdministrator admin,RecordArchive archive) { this.jdbc=jdbc;this.admin=admin;this.archive=archive; }
    public RpcResult<List<Map<String,Object>>> status() {
        admin.require();
        List<Map<String,Object>> result=new ArrayList<>();
        result.add(Map.of("type","configuration","deleteEnabled",archive.deletionEnabled(),"retentionDays",archive.retentionDays()));
        for(String table:RecordArchive.TABLES) {
            var row=new LinkedHashMap<String,Object>();
            row.put("type","backlog");row.put("table",table);
            row.putAll(jdbc.queryForMap("SELECT COUNT(*) AS oldRows, MIN(create_at) AS oldestAt FROM "+table+" WHERE create_at<?",LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(archive.retentionDays()).atStartOfDay()));
            result.add(row);
        }
        jdbc.queryForList("SELECT * FROM bi_work_request ORDER BY created_at DESC LIMIT 30").forEach(row->{row.put("type","job");result.add(row);});
        jdbc.queryForList("SELECT * FROM bi_archive_batch ORDER BY created_at DESC LIMIT 30").forEach(row->{row.put("type","batch");result.add(row);});
        return RpcResult.success(result);
    }
    public RpcResult<String> backfill(LocalDate start,LocalDate end) {
        admin.require();
        if(start==null || end==null || start.isAfter(end) || !end.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))) || start.plusYears(1).isBefore(end))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,"回填范围为已结束日期，最多一年");
        return RpcResult.success(enqueue("BACKFILL",start,end,false));
    }
    public RpcResult<String> archive(boolean dryRun) {
        admin.require();
        if(!dryRun && !archive.deletionEnabled()) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"删除开关未开启，请先执行归档演练");
        return RpcResult.success(enqueue("ARCHIVE",null,null,dryRun));
    }
    public RpcResult<String> retry(String jobId) {
        admin.require();
        int updated=jdbc.update("UPDATE bi_work_request SET status='QUEUED',error_message=NULL,finished_at=NULL WHERE id=? AND status='FAILED'",jobId);
        if(updated!=1) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"仅失败任务可以重试");
        return RpcResult.success(jobId);
    }
    private String enqueue(String kind,LocalDate start,LocalDate end,boolean dry) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO bi_work_request(id,kind,start_date,end_date,dry_run,status,requested_by) VALUES(?,?,?,?,?,'QUEUED',?)",
            id,kind,start,end,dry,xyz.jasenon.lab.auth.context.UserContextHolder.get().getUserId());
        return id;
    }
}
