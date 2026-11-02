package xyz.jasenon.lab.edu.service;

import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.edu.api.command.TimetableExportQuery;
import xyz.jasenon.lab.edu.api.command.TimetableListQuery;
import xyz.jasenon.lab.edu.api.model.WeekType;
import xyz.jasenon.lab.edu.api.view.SemesterView;
import xyz.jasenon.lab.edu.api.view.TimetableView;
import xyz.jasenon.lab.edu.mapper.SemesterMapper;
import xyz.jasenon.lab.edu.model.Semester;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimetableExportTests {
    private final SemesterView semester = new SemesterView("s1", "2026-2027 第1学期",
            LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 16), null, null);

    @AfterEach void clearContext() { UserContextHolder.clear(); }

    @Test void printableWorkbookPreservesParallelCoursesMergesAndPrintSettings() throws Exception {
        var first = course("a", WeekType.Single);
        var second = course("b", WeekType.Double);
        byte[] content = TimetableWorkbookExporter.write(semester, List.of("lab-1", "lab-2"),
                Map.of("lab-1", "16/201 实验室", "lab-2", "16/201 实验室"), List.of(first, second));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertNotEquals(workbook.getSheetName(0), workbook.getSheetName(1));
            assertFalse(workbook.getSheetName(0).contains("/"));
            var sheet = workbook.getSheetAt(0);
            String single = sheet.getRow(4).getCell(1).getStringCellValue();
            String doubled = sheet.getRow(4).getCell(2).getStringCellValue();
            assertTrue(single.contains(first.courseName()));
            assertTrue(single.contains(first.teacherName()));
            assertTrue(single.contains("1–16 周 · 单周"));
            assertTrue(doubled.contains("1–16 周 · 双周"));
            assertTrue(single.contains("08:00–09:40"));
            assertTrue(sheet.getRow(4).getCell(1).getCellStyle().getWrapText());
            assertTrue(sheet.getMergedRegions().stream().anyMatch(region -> region.formatAsString().equals("B5:B6")));
            assertTrue(sheet.getMergedRegions().stream().anyMatch(region -> region.formatAsString().equals("C5:C6")));
            assertTrue(sheet.getPrintSetup().getLandscape());
            assertEquals(PrintSetup.A3_PAPERSIZE, sheet.getPrintSetup().getPaperSize());
            assertEquals(1, sheet.getPrintSetup().getFitWidth());
            assertEquals(1, sheet.getPrintSetup().getFitHeight());
            assertNotNull(workbook.getPrintArea(0));
            assertEquals(3, sheet.getRepeatingRows().getLastRow());
            assertEquals("本学期暂无排课", workbook.getSheetAt(1).getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test void longSingleSectionCoursesHaveEnoughRowHeightAndAreLiteralText() throws Exception {
        var course = course("=SUM(1,2)", WeekType.Both);
        var shortCourse = new TimetableView(course.id(), "s1", semester, "lab-1", "实验室",
                "=SUM(1,2) 人工智能与Python程序设计综合实验课程", "张老师、王老师、欧阳老师", WeekType.Both,
                1, 16, 1, 1, LocalTime.of(8, 0), LocalTime.of(8, 45), 1);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(TimetableWorkbookExporter.write(
                semester, List.of("lab-1"), Map.of(), List.of(shortCourse))))) {
            var sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getRow(4).getHeightInPoints() > 48);
            assertTrue(sheet.getRow(4).getCell(1).getStringCellValue().contains(shortCourse.courseName()));
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, sheet.getRow(4).getCell(1).getCellType());
        }
    }

    @Test void serviceRejectsMissingTargetsInvisibleLabsAndMissingSemesterBeforeQuerying() {
        UserContextHolder.set(UserContext.builder().userId("u1").laboratoryIds(List.of("lab-1")).build());
        SemesterMapper mapper = (SemesterMapper) Proxy.newProxyInstance(SemesterMapper.class.getClassLoader(),
                new Class<?>[]{SemesterMapper.class}, (proxy, method, args) -> null);
        var service = new TimetableServiceImpl(mapper, null, 5242880) {
            @Override public RpcResult<List<TimetableView>> list(TimetableListQuery query) {
                fail("No courses should be read before validation");
                return null;
            }
        };
        assertTrue(assertThrows(BusinessException.class, () -> service.exportExcel(new TimetableExportQuery("s1", List.of()))).getMessage().contains("请选择"));
        assertTrue(assertThrows(BusinessException.class, () -> service.exportExcel(new TimetableExportQuery("s1", List.of("lab-1", "hidden")))).getMessage().contains("无权访问"));
        assertTrue(assertThrows(BusinessException.class, () -> service.exportExcel(new TimetableExportQuery("missing", List.of("lab-1")))).getMessage().contains("学期不存在"));
    }

    @Test void serviceExportsOnlyExplicitNormalizedTargets() throws Exception {
        UserContextHolder.set(UserContext.builder().userId("u1").laboratoryIds(List.of("lab-1", "lab-2")).build());
        var model = new Semester();
        model.setId("s1"); model.setName(semester.name());
        model.setStartDate(semester.startDate()); model.setEndDate(semester.endDate());
        SemesterMapper mapper = (SemesterMapper) Proxy.newProxyInstance(SemesterMapper.class.getClassLoader(),
                new Class<?>[]{SemesterMapper.class}, (proxy, method, args) -> model);
        var service = new TimetableServiceImpl(mapper, null, 5242880) {
            @Override public RpcResult<List<TimetableView>> list(TimetableListQuery query) {
                assertEquals(List.of("lab-1"), query.laboratoryIds());
                return RpcResult.success(List.of(course("a", WeekType.Single)));
            }
        };
        byte[] content = service.exportExcel(new TimetableExportQuery("s1", List.of(" lab-1 ", "lab-1"))).data();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals(1, workbook.getNumberOfSheets());
        }
    }

    private TimetableView course(String id, WeekType weekType) {
        return new TimetableView(id, "s1", semester, "lab-1", "实验室", "人工智能与Python程序设计（实验）" + id,
                "张慧丽、王晓明", weekType, 1, 16, 1, 2, LocalTime.of(8, 0), LocalTime.of(9, 40), 1);
    }
}
