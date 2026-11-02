package xyz.jasenon.lab.bi.telemetry;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import xyz.jasenon.lab.storage.*;
import xyz.jasenon.lab.bi.service.TelemetryServiceImpl;
import xyz.jasenon.lab.bi.api.query.TelemetryQuery;
import xyz.jasenon.lab.auth.context.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.assertj.core.api.Assertions.*;
import static xyz.jasenon.lab.bi.telemetry.TelemetryRepository.*;

@EnabledIfEnvironmentVariable(named="BI_TEST_JDBC_URL",matches=".+")
class RecordArchiveMysqlTests {
    JdbcTemplate jdbc;
    BiLease lease;
    TelemetryRepository repository;
    TelemetryRollup rollup;
    MemoryStorage storage;
    RecordArchive archive;
    String token;
    LocalDate old=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(100);
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("BI_TEST_JDBC_URL");
        if(!url.contains("/bi_test")) throw new IllegalStateException("Integration tests require an isolated bi_test database");
        var ds=new DriverManagerDataSource(url,"root","");
        jdbc=new JdbcTemplate(ds);
        Path root=Path.of("").toAbsolutePath();
        while(!Files.exists(root.resolve("sql/schema.sql"))) root=root.getParent();
        try(var connection=ds.getConnection()) { ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("sql/schema.sql"))); }
        for(String table:List.of("bi_projection_cursor","bi_work_request","bi_archive_batch","bi_archive_anchor","bi_device_hourly","bi_summary_day","bi_job_lease","device","laboratory"))
            jdbc.update("DELETE FROM "+table);
        RecordArchive.TABLES.forEach(table->jdbc.update("DELETE FROM "+table));
        lease=new BiLease(jdbc,new DataSourceTransactionManager(ds));repository=new TelemetryRepository(jdbc);rollup=new TelemetryRollup(repository,jdbc,lease,15);
        storage=new MemoryStorage();var beans=new StaticListableBeanFactory(Map.of("storage",storage));
        archive=new RecordArchive(jdbc,rollup,lease,beans.getBeanProvider(ObjectStorage.class),
            new StorageProperties(null,null,"test","test",null,null,null),true,2,90);
        token=lease.acquire();
        UserContextHolder.set(UserContext.builder().userId("u").laboratoryIds(List.of("lab")).build());
    }
    @AfterEach void cleanup() { UserContextHolder.clear(); if(token!=null) lease.release(token); }
    void device(String id,String kind,String lab) {
        jdbc.update("INSERT INTO device(id,device_name,belong_to,device_type,address,create_at) VALUES(?,?,?,?,?,?)",
            id,id,lab,kind,kind.equals("AirCondition")?31:11,old.minusDays(1).atStartOfDay());
    }
    void air(String id,String d,LocalDateTime at,boolean open) {
        jdbc.update("INSERT INTO air_condition_record(id,device_id,address,self_id,is_open,temperature,room_temperature,error_code,create_at) VALUES(?,?,31,0,?,24,26,3,?)",id,d,open,at);
    }
    void energy(String id,String d,LocalDateTime at,double value) {
        jdbc.update("INSERT INTO circuit_break_record(id,device_id,address,is_open,is_fix,is_lock,voltage,current,power,energy,leakage,temperature,create_at) VALUES(?,?,11,1,0,0,220,1,220,?,0,25,?)",id,d,value,at);
    }
    void seed() {
        device("a","AirCondition","lab");device("e","CircuitBreak","lab");
        air("a1","a",old.atStartOfDay(),true);air("a2","a",old.atTime(0,10),false);air("a3","a",LocalDateTime.now(),false);
        energy("e1","e",old.atStartOfDay(),10);energy("e2","e",old.atTime(1,0),12);energy("e3","e",LocalDateTime.now(),12);
    }
    @Test void verifiedArchivePreservesLatestStateAndRollupsAcrossRepeatRuns() throws Exception {
        seed();archive.run(false,token);
        assertThat(jdbc.queryForList("SELECT id FROM air_condition_record",String.class)).containsExactly("a3");
        assertThat(jdbc.queryForList("SELECT id FROM circuit_break_record",String.class)).containsExactly("e3");
        assertThat(jdbc.queryForObject("SELECT SUM(deleted_count) FROM bi_archive_batch",Integer.class)).isEqualTo(4);
        assertThat(repository.saved(old,old,"AIR_CONDITION").stream().mapToDouble(h->h.value().runningSeconds).sum()).isEqualTo(600);
        assertThat(repository.saved(old,old,"ENERGY").stream().mapToDouble(h->h.value().energyKwh).sum()).isEqualTo(2);
        String before=json(repository.saved(old,old,"ENERGY"));
        archive.run(false,token);rollup.day(old,token);
        assertThat(json(repository.saved(old,old,"ENERGY"))).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bi_archive_anchor",Integer.class)).isEqualTo(2);
        // Objects contain byte-exact, versioned original SQL values and can be read after deletion.
        for(var batch:jdbc.queryForList("SELECT * FROM bi_archive_batch WHERE status='COMPLETE'")) {
            String key=(String)batch.get("object_key");var meta=readMap(new String(storage.objects.get(key+".manifest.json"),java.nio.charset.StandardCharsets.UTF_8));
            assertThat(meta.get("sha256")).isEqualTo(storage.sha256("bucket",key));
            try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(storage.get("bucket",key))))) {
                assertThat(reader.lines().count()).isEqualTo(((Number)batch.get("row_count")).longValue());
            }
        }
    }
    @Test void dryRunAndDisabledDeletionNeverRemoveRows() throws Exception {
        seed();archive.run(true,token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM air_condition_record",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bi_summary_day WHERE sealed=1",Integer.class)).isZero();
        var beans=new StaticListableBeanFactory(Map.of("storage",storage));
        var disabled=new RecordArchive(jdbc,rollup,lease,beans.getBeanProvider(ObjectStorage.class),new StorageProperties(null,null,null,null,null,null,null),false,2,90);
        assertThatThrownBy(()->disabled.run(false,token)).hasMessageContaining("disabled");
    }
    @Test void uploadFailureOrChecksumFailureNeverDeletes() throws Exception {
        seed();storage.failUpload=true;
        assertThatThrownBy(()->archive.run(false,token)).hasMessageContaining("upload");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM air_condition_record",Integer.class)).isEqualTo(3);
        storage.failUpload=false;storage.corruptRead=true;
        assertThatThrownBy(()->archive.run(false,token)).hasMessageContaining("checksum");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM air_condition_record",Integer.class)).isEqualTo(3);
    }
    @Test void modifiedSourceIsPreservedAndRetryResumesAlreadyDeletedRows() throws Exception {
        seed();
        storage.afterVerified=()->jdbc.update("UPDATE air_condition_record SET temperature=27 WHERE id='a2'");
        assertThatThrownBy(()->archive.run(false,token)).hasMessageContaining("version changed");
        assertThat(jdbc.queryForList("SELECT id FROM air_condition_record",String.class)).contains("a2","a3");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bi_archive_batch WHERE status='FAILED'",Integer.class)).isEqualTo(1);
        // Reconcile the test mutation to the exact archived version; a retry must not recount a1.
        var payload=storage.objects.entrySet().stream().filter(e->e.getKey().endsWith(".ndjson.gz")).findFirst().orElseThrow();
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(payload.getValue()))))) {
            reader.lines().map(TelemetryRepository::readMap).map(e->(Map<?,?>)e.get("row")).filter(row->row.get("id").equals("a2")).forEach(row->
                jdbc.update("UPDATE air_condition_record SET temperature=?,update_at=? WHERE id='a2'",row.get("temperature"),time(row.get("update_at"))));
        }
        storage.afterVerified=null;archive.run(false,token);
        assertThat(jdbc.queryForObject("SELECT SUM(deleted_count) FROM bi_archive_batch WHERE table_name='air_condition_record'",Integer.class)).isEqualTo(2);
    }
    @Test void leaseExcludesOtherWorkersAndFencesExpiredWorker() {
        assertThat(lease.acquire()).isNull();
        jdbc.update("UPDATE bi_job_lease SET expires_at=DATE_SUB(NOW(3),INTERVAL 1 SECOND)");
        String second=lease.acquire();
        assertThat(second).isNotNull().isNotEqualTo(token);
        assertThatThrownBy(()->lease.guarded(token,()->jdbc.update("DELETE FROM device"))).hasMessageContaining("lease lost");
        lease.release(token);
        assertThat(lease.acquire()).isNull();lease.release(second);
    }
    @Test void scopeIsolationAndMissingDaysAreVisibleToDashboard() {
        seed();device("hidden","AirCondition","secret");air("secret1","hidden",old.atStartOfDay(),true);
        rollup.day(old,token);
        var service=new TelemetryServiceImpl(repository,rollup,jdbc,Clock.system(ZoneId.of("Asia/Shanghai")));
        var view=service.dashboard(new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,TelemetryQuery.Range.CUSTOM,old,old,List.of("lab","secret"))).data();
        assertThat(view.devices()).extracting(d->d.deviceId()).containsExactly("a");
        assertThat(view.metrics().get("runningHours")).isEqualTo(1.0/6);
        assertThat(view.laboratoryRanking()).extracting(p->p.key()).containsExactly("lab");
        var missing=service.dashboard(new TelemetryQuery(TelemetryQuery.Kind.ENERGY,TelemetryQuery.Range.CUSTOM,old.minusDays(1),old,List.of())).data();
        assertThat(missing.qualityNotes()).anyMatch(n->n.contains("回填"));
        var empty=service.dashboard(new TelemetryQuery(TelemetryQuery.Kind.ENERGY,TelemetryQuery.Range.TODAY,null,null,List.of("secret"))).data();
        assertThat(empty.devices()).isEmpty();assertThat(empty.deviceRanking()).isEmpty();
    }
    @Test void allFiveTablesRetainOneOldLatestStateAndArchiveDeletedRows() throws Exception {
        seed();
        jdbc.update("INSERT INTO access_record(id,device_id,address,is_open,is_lock,lock_status,delay_time,create_at) VALUES('x1','access',1,0,0,0,0,?)",old.atStartOfDay());
        jdbc.update("INSERT INTO access_record(id,device_id,address,is_open,is_lock,lock_status,delay_time,create_at) VALUES('x2','access',1,0,0,0,0,?)",old.atTime(1,0));
        jdbc.update("INSERT INTO light_record(id,device_id,address,self_id,is_open,is_lock,create_at) VALUES('l1','light',41,0,0,0,?)",old.atStartOfDay());
        jdbc.update("INSERT INTO light_record(id,device_id,address,self_id,is_open,is_lock,create_at) VALUES('l2','light',41,0,0,0,?)",old.atTime(1,0));
        jdbc.update("INSERT INTO sensor_record(id,device_id,address,self_id,temperature,humidity,light,smoke,create_at,delete_at) VALUES('s1','sensor',61,0,20,50,1,0,?,NOW(3))",old.atStartOfDay());
        jdbc.update("INSERT INTO sensor_record(id,device_id,address,self_id,temperature,humidity,light,smoke,create_at) VALUES('s2','sensor',61,0,20,50,1,0,?)",old.atTime(1,0));
        archive.run(false,token);
        assertThat(jdbc.queryForList("SELECT id FROM access_record",String.class)).containsExactly("x2");
        assertThat(jdbc.queryForList("SELECT id FROM light_record",String.class)).containsExactly("l2");
        assertThat(jdbc.queryForList("SELECT id FROM sensor_record",String.class)).containsExactly("s2");
        assertThat(archive.hasBacklog()).isFalse();
    }
    @Test void changedReadingAfterOutageEnqueuesAffectedDatesExactlyOnce() {
        seed();
        jdbc.update("INSERT INTO bi_projection_cursor(device_id,record_id,observed_at) VALUES('e','e2',?)",old.atTime(1,0));
        var jobs=new BiJobs(jdbc,lease,rollup,archive);
        jobs.enqueueChangedBoundaries(token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bi_work_request",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT start_date FROM bi_work_request",java.sql.Date.class).toLocalDate()).isEqualTo(old);
        jobs.enqueueChangedBoundaries(token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bi_work_request",Integer.class)).isEqualTo(1);
    }
    @Test void rollupReplacesWholeDayWithoutDoubleCountingAndTodayDoesNotDuplicateIt() {
        seed();rollup.day(old,token);
        String before=json(repository.saved(old,old,"AIR_CONDITION"));
        rollup.day(old,token);
        assertThat(json(repository.saved(old,old,"AIR_CONDITION"))).isEqualTo(before);
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        // An accidental persisted current-day projection must be ignored by the live dashboard.
        var bogus=new TelemetryMath.Hour(today.atStartOfDay());bogus.runningSeconds=999999;
        repository.save(repository.devices().get(0),bogus);
        var service=new TelemetryServiceImpl(repository,rollup,jdbc,Clock.system(ZoneId.of("Asia/Shanghai")));
        var view=service.dashboard(new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,TelemetryQuery.Range.TODAY,null,null,List.of("lab"))).data();
        assertThat(view.metrics().get("runningHours")).isZero();
    }
    @Test void restoreToolVerifiesEveryFieldIntoAnIsolatedTable() throws Exception {
        seed();
        Map<String,Map<String,Object>> original=new HashMap<>();
        jdbc.queryForList("SELECT * FROM air_condition_record").forEach(row->original.put(row.get("id").toString(),RecordArchive.canonical(row)));
        archive.run(false,token);
        Path directory=Files.createTempDirectory("bi-restore-test-");
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("tools/bi/restore-archive.py"))) root=root.getParent();
        for(var batch:jdbc.queryForList("SELECT * FROM bi_archive_batch WHERE table_name='air_condition_record' AND status='COMPLETE'")) {
            String key=batch.get("object_key").toString(),id=batch.get("id").toString();
            Path gzip=directory.resolve(id+".gz"),manifest=directory.resolve(id+".json"),sql=directory.resolve(id+".sql");
            Files.write(gzip,storage.objects.get(key));Files.write(manifest,storage.objects.get(key+".manifest.json"));
            String command="python3 "+quote(root.resolve("tools/bi/restore-archive.py"))+" --archive "+quote(gzip)+" --manifest "+quote(manifest)+" --output "+quote(sql);
            Process process=new ProcessBuilder("zsh","-lic",command).redirectErrorStream(true).start();
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.waitFor()).withFailMessage(output).isZero();
            try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
                ScriptUtils.executeSqlScript(connection,new FileSystemResource(sql));
            }
            String target="bi_restore_"+id.replace("-","");
            for(var restored:jdbc.queryForList("SELECT * FROM "+target)) assertThat(RecordArchive.canonical(restored)).isEqualTo(original.get(restored.get("id").toString()));
            jdbc.execute("DROP TABLE "+target);
            Files.write(gzip,new byte[]{0,1,2});
            Process corrupt=new ProcessBuilder("zsh","-lic",command).redirectErrorStream(true).start();
            corrupt.getInputStream().readAllBytes();
            assertThat(corrupt.waitFor()).isNotZero();
        }
        try(var files=Files.list(directory)) { for(Path path:files.toList()) Files.delete(path); }Files.delete(directory);
    }
    private static String quote(Path path) { return "'"+path.toString().replace("'","'\\''")+"'"; }
    void seedReport() {
        seed();
        jdbc.update("INSERT INTO laboratory(id,laboratory_name,building_name,org_name) VALUES('lab','电气实验室','楼 A','电气学院'),('lab-2','计算实验室','楼 B','计算机学院'),('secret','隐藏实验室','隐藏楼','隐藏单位')");
        device("a-other","AirCondition","lab-2");device("hidden","AirCondition","secret");
        air("other-1","a-other",old.atStartOfDay(),true);air("other-2","a-other",old.atTime(0,5),false);
        air("hidden-1","hidden",old.atStartOfDay(),true);
        jdbc.update("UPDATE device SET device_name='同名内机',group_id='AC-01' WHERE id IN ('a','a-other')");
        UserContextHolder.set(UserContext.builder().userId("u").laboratoryIds(List.of("lab","lab-2")).build());
        rollup.day(old,token);
    }
    @Test void businessFiltersIntersectAndReportTotalEqualsAllRows() {
        seedReport();
        var service=new TelemetryServiceImpl(repository,rollup,jdbc,Clock.system(ZoneId.of("Asia/Shanghai")));
        var query=new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,TelemetryQuery.Range.CUSTOM,old,old,
            List.of("lab","lab-2","secret"),List.of("楼 A"),List.of("电气学院"),List.of("a","hidden"));
        var view=service.dashboard(query).data();
        assertThat(view.rows()).hasSize(1);
        var row=view.rows().get(0);
        assertThat(row.deviceId()).isEqualTo("a");assertThat(row.deviceName()).isEqualTo("同名内机");
        assertThat(row.groupId()).isEqualTo("AC-01");assertThat(row.laboratoryName()).isEqualTo("电气实验室");
        assertThat(row.buildingName()).isEqualTo("楼 A");assertThat(row.orgName()).isEqualTo("电气学院");
        assertThat(view.metrics().get("runningHours")).isEqualTo(view.rows().stream().mapToDouble(r->r.value()).sum()).isEqualTo(1.0/6);
        var impossible=new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,TelemetryQuery.Range.CUSTOM,old,old,List.of(),List.of("楼 A"),List.of("计算机学院"),List.of());
        assertThat(service.dashboard(impossible).data().rows()).isEmpty();
        var forbidden=new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,TelemetryQuery.Range.CUSTOM,old,old,List.of(),List.of(),List.of(),List.of("hidden"));
        assertThat(service.dashboard(forbidden).data().rows()).isEmpty();
    }
    @Test void filterOptionsAreScopedCascadedAndUseIdsForDuplicateNames() {
        seedReport();
        var service=new TelemetryServiceImpl(repository,rollup,jdbc,Clock.system(ZoneId.of("Asia/Shanghai")));
        var all=service.filterOptions(new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,null,null,null,List.of())).data();
        assertThat(all.buildings()).extracting(o->o.value()).containsExactly("楼 A","楼 B");
        assertThat(all.organizations()).extracting(o->o.value()).doesNotContain("隐藏单位");
        assertThat(all.devices()).extracting(o->o.value()).containsExactlyInAnyOrder("a","a-other");
        assertThat(all.devices()).extracting(o->o.label()).containsOnly("同名内机");
        var restricted=service.filterOptions(new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,null,null,null,List.of(),List.of("楼 B"),List.of(),List.of())).data();
        assertThat(restricted.laboratories()).extracting(o->o.value()).containsExactly("lab-2");
        assertThat(restricted.devices()).extracting(o->o.value()).containsExactly("a-other");
    }
    @Test void emptyDimensionsMeanAllAndArchivedRowsStillHaveBusinessMetadata() throws Exception {
        seedReport();
        var service=new TelemetryServiceImpl(repository,rollup,jdbc,Clock.system(ZoneId.of("Asia/Shanghai")));
        var query=new TelemetryQuery(TelemetryQuery.Kind.AIR_CONDITION,TelemetryQuery.Range.CUSTOM,old,old,List.of());
        var before=service.dashboard(query).data();
        assertThat(before.rows()).extracting(r->r.deviceId()).containsExactly("a","a-other");
        assertThat(before.metrics().get("runningHours")).isEqualTo(before.rows().stream().mapToDouble(r->r.value()).sum());
        archive.run(false,token);
        assertThat(json(service.dashboard(query).data().rows())).isEqualTo(json(before.rows()));
        var energy=service.dashboard(new TelemetryQuery(TelemetryQuery.Kind.ENERGY,TelemetryQuery.Range.CUSTOM,old,old,List.of())).data();
        assertThat(energy.rows()).extracting(r->r.deviceId()).containsExactly("e");
        assertThat(energy.metrics().get("energyKwh")).isEqualTo(energy.rows().stream().mapToDouble(r->r.value()).sum()).isEqualTo(2);
    }
    static class MemoryStorage implements ObjectStorage {
        Map<String,byte[]> objects=new HashMap<>();
        boolean failUpload,corruptRead;
        Runnable afterVerified;
        int reads;
        public void put(String bucket,String key,InputStream input,long size,String type) throws Exception {
            if(failUpload) throw new IOException("upload failed");objects.put(key,input.readAllBytes());
        }
        public InputStream get(String bucket,String key) {
            byte[] bytes=objects.get(key);
            if(corruptRead) return new ByteArrayInputStream(new byte[]{0});
            // The second archive read is the pre-delete verification, after upload verification.
            if(key.endsWith(".ndjson.gz") && ++reads==2 && afterVerified!=null) afterVerified.run();
            return new ByteArrayInputStream(bytes);
        }
        public String downloadUrl(String bucket,String key,int seconds) { return "https://test/"+key; }
    }
}
