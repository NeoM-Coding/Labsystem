package xyz.jasenon.lab.edu.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.edu.api.command.TimetableImport;
import xyz.jasenon.lab.edu.api.command.TimetableImportTemplateQuery;
import xyz.jasenon.lab.edu.mapper.SemesterMapper;
import xyz.jasenon.lab.edu.model.Semester;
import xyz.jasenon.lab.edu.model.Timetable;
import xyz.jasenon.lab.edu.api.model.WeekType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimetableTemplateTests {
    @AfterEach void clearContext() { UserContextHolder.clear(); }

    private void login() {
        UserContextHolder.set(UserContext.builder().userId("user-1").laboratoryIds(List.of("lab-1")).build());
    }

    @Test void templateHasBlankDataCommentsInstructionsAndOnlyFirstSheetIsParsed() throws Exception {
        var service = new TimetableServiceImpl(null, null, 5 * 1024 * 1024);
        byte[] template = service.importTemplate(new TimetableImportTemplateQuery()).data();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template)); var output = new ByteArrayOutputStream()) {
            assertEquals(2, book.getNumberOfSheets());
            assertEquals("课表数据", book.getSheetName(0));
            assertEquals("填写说明", book.getSheetName(1));
            for (int col = 0; col < 7; col++) {
                var cell = book.getSheetAt(0).getRow(0).getCell(col);
                assertEquals("", cell.toString());
                assertNotNull(cell.getCellComment());
            }
            Object empty = ReflectionTestUtils.invokeMethod(service, "parseWorkbook", template);
            assertTrue(((List<?>) component(empty, "entries")).isEmpty());
            assertTrue(((List<?>) component(empty, "errors")).isEmpty());
            book.getSheetAt(0).getRow(0).getCell(6).setCellValue("课程<>2-16周(双)[1-2节]<>教师");
            book.write(output);
            Object parsed = ReflectionTestUtils.invokeMethod(service, "parseWorkbook", output.toByteArray());
            List<?> entries = component(parsed, "entries");
            assertEquals(1, entries.size());
            assertEquals(7, (int) component(entries.get(0), "weekday"));
            assertEquals(WeekType.Double, component(entries.get(0), "weekType"));
        }
    }

    @Test void rejectsEmptyTemplateInvalidSemesterInvisibleLabAndOversizedContent() {
        login();
        var exists = new java.util.concurrent.atomic.AtomicBoolean(false);
        var mapper = mapper(exists);
        var service = new TimetableServiceImpl(mapper, null, 5 * 1024 * 1024);
        byte[] template = service.importTemplate(new TimetableImportTemplateQuery()).data();
        var command = new TimetableImport("s1", "lab-1", "课程.xlsx", template);
        assertTrue(assertThrows(BusinessException.class, () -> service.importExcel(command)).getMessage().contains("学期不存在"));
        exists.set(true);
        assertTrue(assertThrows(BusinessException.class, () -> service.importExcel(command)).getMessage().contains("没有可导入"));
        assertTrue(assertThrows(BusinessException.class, () -> service.importExcel(new TimetableImport("s1", "other", "课程.xlsx", template))).getMessage().contains("无权访问"));
        assertThrows(BusinessException.class, () -> service.importExcel(new TimetableImport("s1", "lab-1", "课程.xlsx", new byte[5 * 1024 * 1024 + 1])));
    }

    @Test void xlsIsStillSupported() throws Exception {
        var service = new TimetableServiceImpl(null, null, 5 * 1024 * 1024);
        try (var book = new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            book.createSheet().createRow(0).createCell(1).setCellValue("课程<>1-8周(单)[10:00-10:45]<>教师");
            book.write(output);
            Object parsed = ReflectionTestUtils.invokeMethod(service, "parseWorkbook", output.toByteArray());
            List<?> entries = component(parsed, "entries");
            assertEquals(1, entries.size());
            assertEquals(2, (int) component(entries.get(0), "weekday"));
        }
    }

    @Test void partialImportContinuesAfterFormatAndConflictFailures() throws Exception {
        login();
        var mapper = mapper(new java.util.concurrent.atomic.AtomicBoolean(true));
        var transactions = new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> callback) {
                return callback.doInTransaction(new SimpleTransactionStatus());
            }
        };
        var existing = new Timetable();
        existing.setId("existing");
        existing.setLaboratoryId("lab-1");
        existing.setTeacherName("existing-teacher");
        existing.setWeekday(1);
        existing.setStartWeek(1); existing.setEndWeek(16); existing.setWeekType(WeekType.Both);
        existing.setStartTime(LocalTime.of(8, 0)); existing.setEndTime(LocalTime.of(9, 40));
        List<Timetable> saved = new ArrayList<>();
        var service = new TimetableServiceImpl(mapper, transactions, 5 * 1024 * 1024) {
            @Override public boolean save(Timetable timetable) { saved.add(timetable); return true; }
            @Override public LambdaQueryChainWrapper<Timetable> lambdaQuery() {
                return new LambdaQueryChainWrapper<Timetable>(Timetable.class) {
                    @Override public LambdaQueryChainWrapper<Timetable> eq(boolean condition,
                            com.baomidou.mybatisplus.core.toolkit.support.SFunction<Timetable, ?> column, Object value) {
                        return this;
                    }
                    @Override public List<Timetable> list() { return List.of(existing); }
                };
            }
        };
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var row = book.createSheet().createRow(0);
            row.createCell(0).setCellValue("冲突课程<>1-16周[1-2节]<>教师,错误格式,成功课程<>1-16周[3-4节]<>教师");
            book.write(output);
            var result = service.importExcel(new TimetableImport("s1", "lab-1", "课程.xlsx", output.toByteArray())).data();
            assertEquals(1, result.ok()); assertEquals(2, result.fail());
            assertEquals("成功课程", saved.get(0).getCourseName());
            assertTrue(result.errors().stream().allMatch(error -> error.rowIndex() == 0 && error.columnIndex() == 0));
            assertTrue(result.errors().stream().anyMatch(error -> error.reason().contains("冲突")));
        }
    }

    private static SemesterMapper mapper(java.util.concurrent.atomic.AtomicBoolean exists) {
        return (SemesterMapper) java.lang.reflect.Proxy.newProxyInstance(SemesterMapper.class.getClassLoader(),
                new Class<?>[]{SemesterMapper.class}, (proxy, method, args) -> {
                    if (method.getName().equals("selectById") || method.getName().equals("selectByIdForUpdate")) {
                        return exists.get() ? new Semester() : null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T component(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (T) method.invoke(record);
    }
}
