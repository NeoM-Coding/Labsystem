package xyz.jasenon.lab.edu.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.edu.api.view.TimetableImportError;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableExcelImportTests {

    @Test
    void realXlsxUsesThreeFieldsAndCollectsInvalidLegacyItems() throws Exception {
        TimetableServiceImpl service = new TimetableServiceImpl(null, null, 5 * 1024 * 1024);
        byte[] workbook = workbook(
                "软件工程<>1-16周[1-2节]<>张三,大学英语<>1-8周(单)[10:00-10:45]<>李四",
                "旧格式课程<>1-16周[1-2节]<>A101<>王五"
        );

        Object parsed = ReflectionTestUtils.invokeMethod(service, "parseWorkbook", workbook);
        assertNotNull(parsed);
        List<?> entries = component(parsed, "entries");
        List<TimetableImportError> errors = component(parsed, "errors");

        assertEquals(2, entries.size());
        assertEquals(1, errors.size());
        assertEquals(1, errors.get(0).columnIndex());
        assertTrue(errors.get(0).reason().contains("课程名称<>时间信息<>教师名称"));

        Object first = entries.get(0);
        assertEquals(1, (int) component(first, "weekday"));
        assertEquals(1, (int) component(first, "startSection"));
        assertEquals(2, (int) component(first, "endSection"));
        assertEquals(LocalTime.of(8, 0), component(first, "startTime"));
        assertEquals(LocalTime.of(9, 40), component(first, "endTime"));
        assertEquals("软件工程", component(first, "courseName"));

        Object second = entries.get(1);
        assertEquals(3, (int) component(second, "startSection"));
        assertEquals(3, (int) component(second, "endSection"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T component(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (T) method.invoke(record);
    }

    private static byte[] workbook(String firstCell, String secondCell) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("课表").createRow(0);
            row.createCell(0).setCellValue(firstCell);
            row.createCell(1).setCellValue(secondCell);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
