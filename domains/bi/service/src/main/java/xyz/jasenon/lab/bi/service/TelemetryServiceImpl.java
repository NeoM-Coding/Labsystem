package xyz.jasenon.lab.bi.service;

import xyz.jasenon.lab.common.error.ErrorCode;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.bi.api.TelemetryService;
import xyz.jasenon.lab.bi.api.query.TelemetryQuery;
import xyz.jasenon.lab.bi.api.view.TelemetryDashboard;
import xyz.jasenon.lab.bi.api.view.TelemetryDashboard.*;
import xyz.jasenon.lab.bi.telemetry.*;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static xyz.jasenon.lab.bi.telemetry.TelemetryRepository.*;

@DubboService
public class TelemetryServiceImpl implements TelemetryService {
    private final TelemetryRepository repository;
    private final TelemetryRollup rollup;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public TelemetryServiceImpl(TelemetryRepository repository,TelemetryRollup rollup,JdbcTemplate jdbc,Clock clock) {
        this.repository=repository;this.rollup=rollup;this.jdbc=jdbc;this.clock=clock;
    }
    @Override @ActionAuthorized
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public RpcResult<TelemetryDashboard> dashboard(TelemetryQuery q) {
        if (q==null || q.kind()==null || q.range()==null) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"请选择统计范围");
        var context=UserContextHolder.get();
        if (context==null || context.getUserId()==null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED,"请先登录");
        var now=LocalDateTime.now(clock); var today=now.toLocalDate();
        LocalDate start=switch(q.range()) { case TODAY -> today;case CURRENT_MONTH -> today.withDayOfMonth(1);case CUSTOM -> q.startDate();};
        LocalDate end=q.range()==TelemetryQuery.Range.CUSTOM ? q.endDate() : today;
        if (start==null || end==null || start.isAfter(end) || end.isAfter(today) || start.plusYears(1).isBefore(end))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,"日期范围不得超过一年或包含未来日期");
        Map<String,Laboratory> laboratoryMetadata=visibleLaboratories();
        Set<String> labs=effectiveLaboratories(q,laboratoryMetadata);
        Set<String> selectedDevices=normalized(q.deviceIds());
        boolean air=q.kind()==TelemetryQuery.Kind.AIR_CONDITION;
        String kind=q.kind().name();
        Map<String,Device> allDevices=repository.devices().stream().filter(d->d.kind().equals(kind))
            .collect(Collectors.toMap(Device::id,d->d));
        List<Device> devices=allDevices.values().stream().filter(d -> labs.contains(d.laboratoryId())
            && (selectedDevices.isEmpty() || selectedDevices.contains(d.id()))
            && d.createdAt().isBefore(end.plusDays(1).atStartOfDay())
            && (d.deletedAt()==null || d.deletedAt().isAfter(start.atStartOfDay()))).toList();
        Map<String,String> deviceNames=devices.stream().collect(Collectors.toMap(Device::id,d -> d.name()==null?d.id():d.name()));
        Map<String,String> labNames=new HashMap<>();
        laboratoryMetadata.values().forEach(lab -> labNames.put(lab.id(),lab.name()));
        Map<UsageKey,UsageAccumulator> usage=new HashMap<>();
        for(Device device:devices) usage.put(new UsageKey(device.id(),device.laboratoryId()),new UsageAccumulator());
        List<SavedHour> hours=new ArrayList<>();

        if(!end.isBefore(today)) for(Device d:devices) {
            LocalDateTime liveStart=d.createdAt().isAfter(today.atStartOfDay())?d.createdAt():today.atStartOfDay();
            LocalDateTime liveEnd=d.deletedAt()!=null && d.deletedAt().isBefore(now)?d.deletedAt():now;
            if(!liveStart.isBefore(liveEnd)) continue;
            var values=TelemetryMath.calculate(repository.samples(d,liveStart,liveEnd),liveStart,liveEnd,air,rollup.staleAfter());
            values.forEach(h -> hours.add(new SavedHour(d.id(),d.laboratoryId(),kind,h)));
        }
        Set<LocalDate> ready=new HashSet<>(jdbc.query("SELECT summary_date FROM bi_summary_day WHERE summary_date>=? AND summary_date<=?",(rs,n)->rs.getDate(1).toLocalDate(),start,end));
        boolean missing=false;
        for(LocalDate day=start;day.isBefore(today)&&!day.isAfter(end);day=day.plusDays(1)) if(!ready.contains(day)) missing=true;
        Map<String,Double> metrics=new LinkedHashMap<>(), timeline=new TreeMap<>(), labRanking=new HashMap<>(), deviceRanking=new HashMap<>(), modes=new TreeMap<>();
        Map<String,Boolean> timelineQuality=new HashMap<>();
        List<Fault> faults=new ArrayList<>();
        double running=0,observed=0,energy=0,energyCovered=0,temperature=0,room=0,peak=0,resets=0,expected=0;
        boolean allocated=false;
        try(var historic=repository.streamSaved(start,end.isBefore(today)?end:today.minusDays(1),kind,labs,selectedDevices);
            var all=java.util.stream.Stream.concat(historic,hours.stream())) {
          var iterator=all.iterator();
          while(iterator.hasNext()) {
            SavedHour saved=iterator.next();
            if(!selectedDevices.isEmpty() && !selectedDevices.contains(saved.deviceId())) continue;
            var h=saved.value();
            UsageAccumulator row=usage.computeIfAbsent(new UsageKey(saved.deviceId(),saved.laboratoryId()),key->new UsageAccumulator());
            row.value+=air?h.runningSeconds/3600:h.energyKwh;
            row.expected+=h.expectedSeconds;
            row.covered+=air?h.observedSeconds:h.energyCoveredSeconds;
            row.incomplete|=h.resets>0;
            double value=air?h.runningSeconds/3600:h.energyKwh;
            String key=q.range()==TelemetryQuery.Range.TODAY?h.hour.toString():h.hour.toLocalDate().toString();
            timeline.merge(key,value,Double::sum); labRanking.merge(saved.laboratoryId(),value,Double::sum);deviceRanking.merge(saved.deviceId(),value,Double::sum);
            expected+=h.expectedSeconds;running+=h.runningSeconds;observed+=h.observedSeconds;energy+=h.energyKwh;energyCovered+=h.energyCoveredSeconds;
            temperature+=h.temperatureSeconds;room+=h.roomTemperatureSeconds;peak=Math.max(peak,h.peakPowerW);resets+=h.resets;allocated|=h.estimated;
            h.modes.forEach((k,v)->modes.merge(k,v/3600,Double::sum));faults.addAll(h.faults);
            if(h.estimated || h.resets>0 || (air?h.observedSeconds:h.energyCoveredSeconds)+0.001<h.expectedSeconds) timelineQuality.put(key,true);
        }
        }
        double coverage=expected==0?1:Math.min(1,(air?observed:energyCovered)/expected);
        List<DeviceState> states=new ArrayList<>();
        double opened=0,load=0;
        for(Device d:devices) {
            if(d.deletedAt()!=null) continue;
            Map<String,Object> raw=repository.latest(d);
            if(raw.isEmpty()) { states.add(new DeviceState(d.id(),deviceNames.get(d.id()),d.laboratoryId(),null,true,false,Map.of())); continue; }
            var sample=sample(raw); boolean stale=sample.time().plus(rollup.staleAfter()).isBefore(now) || sample.time().isAfter(now);
            if(!stale) { if(sample.opened()) opened++;load+=sample.power()/1000; }
            Map<String,Double> values=new LinkedHashMap<>();
            for(String field:air?List.of("temperature","room_temperature","error_code"):List.of("voltage","current","power","energy","leakage","temperature")) values.put(field,number(raw.get(field)));
            states.add(new DeviceState(d.id(),deviceNames.get(d.id()),d.laboratoryId(),sample.time(),stale,sample.opened(),values));
        }
        metrics.put("deviceCount",(double)states.size());metrics.put("coverage",coverage);metrics.put("openedDevices",opened);
        metrics.put("runningHours",running/3600);metrics.put("energyKwh",energy);metrics.put("loadKw",load);metrics.put("peakKw",peak/1000);metrics.put("resets",resets);
        if(running>0) { metrics.put("temperature",temperature/running);metrics.put("roomTemperature",room/running); }
        metrics.put("faultDevices",(double)faults.stream().map(Fault::deviceId).distinct().count());
        // Fill absent buckets but mark them as unknown rather than asserting zero measured usage.
        for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)) {
            if(q.range()==TelemetryQuery.Range.TODAY) for(int hour=0;hour<=now.getHour();hour++) {
                String key=day.atTime(hour,0).toString();if(!timeline.containsKey(key)) timelineQuality.put(key,true);timeline.putIfAbsent(key,0.0);
            } else { String key=day.toString();if(!timeline.containsKey(key)) timelineQuality.put(key,true);timeline.putIfAbsent(key,0.0); }
        }
        List<String> notes=new ArrayList<>();
        if(missing) notes.add("部分历史日期尚未完成汇总，请联系管理员回填");
        if(coverage<0.999) notes.add("存在断报或缺少边界读数，缺失区间不代表零使用");
        if(resets>0) notes.add("检测到电表清零或换表，负差区间已跳过");
        if(allocated) notes.add("跨小时或长间隔电量按时间比例分摊");
        if(states.stream().anyMatch(DeviceState::stale)) notes.add("部分设备当前读数已过期，未计入当前负载或开启数量");
        List<Fault> mergedFaults=new ArrayList<>();
        faults.sort(Comparator.comparing(Fault::deviceId).thenComparing(Fault::startAt));
        for(Fault fault:faults) {
            int last=mergedFaults.size()-1;
            if(last>=0 && mergedFaults.get(last).deviceId().equals(fault.deviceId()) && mergedFaults.get(last).errorCode()==fault.errorCode()
                && mergedFaults.get(last).endAt().equals(fault.startAt())) {
                Fault previous=mergedFaults.get(last);
                mergedFaults.set(last,new Fault(fault.deviceId(),previous.startAt(),fault.endAt(),fault.errorCode()));
            } else mergedFaults.add(fault);
        }
        boolean missingDays=missing;
        List<UsageRow> rows=usage.entrySet().stream().map(entry -> {
            UsageKey key=entry.getKey();UsageAccumulator value=entry.getValue();
            Device device=allDevices.get(key.deviceId());
            Laboratory lab=laboratoryMetadata.get(key.laboratoryId());
            double rowCoverage=value.expected==0?0:Math.min(1,value.covered/value.expected);
            return new UsageRow(key.deviceId(),device==null?key.deviceId():Objects.requireNonNullElse(device.name(),device.id()),
                device==null?null:device.groupId(),key.laboratoryId(),lab==null?key.laboratoryId():lab.name(),
                lab==null?null:lab.orgName(),lab==null?null:lab.buildingName(),value.value,
                missingDays || value.incomplete || rowCoverage<0.999,rowCoverage);
        }).sorted(Comparator.comparingDouble(UsageRow::value).reversed().thenComparing(UsageRow::deviceId)
            .thenComparing(UsageRow::laboratoryId)).toList();
        return RpcResult.success(new TelemetryDashboard(kind,start,end,now,missing||coverage<0.999||resets>0,notes,metrics,
            timeline.entrySet().stream().map(e->new Point(e.getKey(),e.getKey(),e.getValue(),timelineQuality.getOrDefault(e.getKey(),false))).toList(),
            ranking(labRanking,labNames),ranking(deviceRanking,deviceNames),ranking(modes,Map.of()),states,mergedFaults,rows));
    }
    private record UsageKey(String deviceId,String laboratoryId) {}
    private static final class UsageAccumulator {
        double value,expected,covered;
        boolean incomplete;
    }

    @Override @ActionAuthorized
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public RpcResult<xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions> filterOptions(TelemetryQuery q) {
        if(q==null || q.kind()==null) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"请选择分析类型");
        var visible=visibleLaboratories();
        Set<String> buildings=normalized(q.buildingNames()),organizations=normalized(q.orgNames());
        var laboratories=visible.values().stream()
            .filter(lab->buildings.isEmpty() || buildings.contains(lab.buildingName()))
            .filter(lab->organizations.isEmpty() || organizations.contains(lab.orgName()))
            .sorted(Comparator.comparing(Laboratory::name).thenComparing(Laboratory::id)).toList();
        Set<String> labs=effectiveLaboratories(q,visible);
        var deviceOptions=repository.devices().stream().filter(device->device.kind().equals(q.kind().name()) && labs.contains(device.laboratoryId()))
            .sorted(Comparator.comparing((Device device)->Objects.requireNonNullElse(device.name(),device.id())).thenComparing(Device::id))
            .map(device->new xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions.Option(device.id(),
                Objects.requireNonNullElse(device.name(),device.id()),visible.get(device.laboratoryId()).name())).toList();
        return RpcResult.success(new xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions(
            namedOptions(visible.values().stream().map(Laboratory::buildingName).toList()),
            namedOptions(visible.values().stream().map(Laboratory::orgName).toList()),
            laboratories.stream().map(lab->new xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions.Option(lab.id(),lab.name(),
                Objects.requireNonNullElse(lab.buildingName(),"未设置楼栋")+" · "+Objects.requireNonNullElse(lab.orgName(),"未设置单位"))).toList(),
            deviceOptions));
    }

    private Map<String,Laboratory> visibleLaboratories() {
        var context=UserContextHolder.get();
        if(context==null || context.getUserId()==null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED,"请先登录");
        Set<String> ids=new HashSet<>(context.filterLaboratoryIds());
        Map<String,Laboratory> result=new HashMap<>(repository.laboratories(ids));
        if(context.getLaboratoryScopes()!=null) for(var lab:context.getLaboratoryScopes()) {
            if(ids.contains(lab.getLaboratoryId())) result.putIfAbsent(lab.getLaboratoryId(),
                new Laboratory(lab.getLaboratoryId(),Objects.requireNonNullElse(lab.getLaboratoryName(),lab.getLaboratoryId()),lab.getBuildingName(),lab.getOrgName()));
        }
        ids.forEach(id->result.putIfAbsent(id,new Laboratory(id,id,null,null)));
        return result;
    }

    private static Set<String> effectiveLaboratories(TelemetryQuery query,Map<String,Laboratory> visible) {
        Set<String> buildingNames=normalized(query.buildingNames()),orgNames=normalized(query.orgNames()),laboratoryIds=normalized(query.laboratoryIds());
        return visible.values().stream()
            .filter(lab->laboratoryIds.isEmpty() || laboratoryIds.contains(lab.id()))
            .filter(lab->buildingNames.isEmpty() || buildingNames.contains(lab.buildingName()))
            .filter(lab->orgNames.isEmpty() || orgNames.contains(lab.orgName()))
            .map(Laboratory::id).collect(Collectors.toSet());
    }

    private static Set<String> normalized(List<String> values) {
        return values==null?Set.of():values.stream().filter(Objects::nonNull).map(String::trim)
            .filter(value->!value.isEmpty()).collect(Collectors.toSet());
    }

    private static List<xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions.Option> namedOptions(List<String> names) {
        return names.stream().filter(Objects::nonNull).filter(name->!name.isBlank()).distinct().sorted()
            .map(name->new xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions.Option(name,name,null)).toList();
    }
    private static List<Point> ranking(Map<String,Double> values,Map<String,String> names) {
        return values.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed())
            .map(e->new Point(e.getKey(),Objects.requireNonNullElse(names.get(e.getKey()),e.getKey()),e.getValue(),false)).toList();
    }
}
