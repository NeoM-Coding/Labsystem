package xyz.jasenon.lab.edu.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.edu.api.view.SemesterView;
import xyz.jasenon.lab.edu.api.view.TimetableView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Print-oriented workbook; intentionally separate from the machine-readable import template. */
final class TimetableWorkbookExporter {
    private static final String[] DAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    private static final String[] TIMES = {"08:00–08:45", "08:55–09:40", "10:00–10:45", "10:55–11:40",
            "14:10–14:55", "15:05–15:50", "16:00–16:45", "16:55–17:40", "18:40–19:25", "19:30–20:15", "20:20–21:05"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private record Placement(TimetableView course, int lane) {}

    private TimetableWorkbookExporter() {}

    static byte[] write(SemesterView semester, List<String> laboratoryIds,
                        Map<String, String> names, List<TimetableView> courses) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var body = style(workbook, 11, false, IndexedColors.WHITE);
            var heading = style(workbook, 11, true, IndexedColors.GREY_25_PERCENT);
            var title = style(workbook, 18, true, IndexedColors.WHITE);
            var subtitle = style(workbook, 10, false, IndexedColors.WHITE);
            var single = style(workbook, 11, false, IndexedColors.LIGHT_TURQUOISE);
            var doubled = style(workbook, 11, false, IndexedColors.LIGHT_YELLOW);
            for (String laboratoryId : laboratoryIds) {
                String name = Optional.ofNullable(names.get(laboratoryId)).filter(value -> !value.isBlank()).orElse(laboratoryId);
                String base = WorkbookUtil.createSafeSheetName(name);
                if (base.isBlank()) base = "实验室";
                String sheetName = base;
                for (int suffix = 2; workbook.getSheet(sheetName) != null; suffix++) {
                    String tail = " (" + suffix + ")";
                    sheetName = base.substring(0, Math.min(base.length(), 31 - tail.length())) + tail;
                }
                var sheet = workbook.createSheet(sheetName);
                List<TimetableView> labCourses = courses.stream().filter(course -> laboratoryId.equals(course.laboratoryId())).toList();
                List<List<Placement>> placements = new ArrayList<>();
                int[] lanes = new int[7];
                for (int day = 1; day <= 7; day++) {
                    int weekday = day;
                    var sorted = labCourses.stream().filter(course -> course.weekday() == weekday)
                            .sorted(Comparator.comparing(TimetableView::startSection).thenComparing(TimetableView::endSection)
                                    .thenComparing(TimetableView::id)).toList();
                    List<Integer> ends = new ArrayList<>();
                    List<Placement> placed = new ArrayList<>();
                    for (var course : sorted) {
                        int lane = 0;
                        while (lane < ends.size() && ends.get(lane) >= course.startSection()) lane++;
                        if (lane == ends.size()) ends.add(course.endSection()); else ends.set(lane, course.endSection());
                        placed.add(new Placement(course, lane));
                    }
                    lanes[day - 1] = Math.max(1, ends.size());
                    placements.add(placed);
                }
                int lastColumn = Arrays.stream(lanes).sum();
                sheet.setColumnWidth(0, 18 * 256);
                for (int col = 1; col <= lastColumn; col++) sheet.setColumnWidth(col, 23 * 256);
                for (int row = 0; row < 15; row++) {
                    var line = sheet.createRow(row);
                    line.setHeightInPoints(row < 4 ? 25 : 48);
                    for (int col = 0; col <= lastColumn; col++) line.createCell(col).setCellStyle(body);
                }
                merge(sheet, 0, 0, 0, lastColumn, name + " · 课程表", title);
                sheet.getRow(0).setHeightInPoints(34);
                merge(sheet, 1, 1, 0, lastColumn, semester.name() + "    " + semester.startDate() + " — " + semester.endDate(), subtitle);
                merge(sheet, 2, 2, 0, lastColumn, "每周 / 单周 / 双周按文字标识执行；同一时段不同周次的课程并排展示。", subtitle);
                merge(sheet, 3, 3, 0, 0, "节次 / 时间", heading);
                for (int section = 1; section <= 11; section++) {
                    merge(sheet, section + 3, section + 3, 0, 0,
                            "第 " + section + " 节\n" + TIMES[section - 1], heading);
                }
                int column = 1;
                for (int day = 0; day < 7; day++) {
                    merge(sheet, 3, 3, column, column + lanes[day] - 1, DAYS[day], heading);
                    for (var placement : placements.get(day)) {
                        var course = placement.course();
                        String weekType = switch (course.weekType()) {
                            case Single -> "单周";
                            case Double -> "双周";
                            case Both -> "每周";
                        };
                        String text = course.courseName() + "\n教师：" + course.teacherName() + "\n"
                                + course.startWeek() + "–" + course.endWeek() + " 周 · " + weekType + "\n"
                                + course.startTime().format(TIME) + "–" + course.endTime().format(TIME);
                        CellStyle courseStyle = switch (course.weekType()) {
                            case Single -> single;
                            case Double -> doubled;
                            case Both -> body;
                        };
                        int col = column + placement.lane();
                        merge(sheet, course.startSection() + 3, course.endSection() + 3, col, col, text, courseStyle);
                        // Excel does not auto-fit merged rows. Reserve enough height for
                        // wrapped Chinese/Latin names, including single-section courses.
                        int lines = Arrays.stream(text.split("\n")).mapToInt(TimetableWorkbookExporter::wrappedLines).sum();
                        float perRow = (lines * 14f + 12) / (course.endSection() - course.startSection() + 1);
                        for (int row = course.startSection() + 3; row <= course.endSection() + 3; row++) {
                            sheet.getRow(row).setHeightInPoints(Math.max(sheet.getRow(row).getHeightInPoints(), perRow));
                        }
                    }
                    column += lanes[day];
                }
                if (labCourses.isEmpty()) sheet.getRow(2).getCell(0).setCellValue("本学期暂无排课");
                sheet.setDisplayGridlines(false);
                sheet.setPrintGridlines(false);
                sheet.createFreezePane(1, 4);
                sheet.setFitToPage(true);
                sheet.setHorizontallyCenter(true);
                sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));
                sheet.setMargin(Sheet.LeftMargin, 0.25);
                sheet.setMargin(Sheet.RightMargin, 0.25);
                sheet.setMargin(Sheet.TopMargin, 0.3);
                sheet.setMargin(Sheet.BottomMargin, 0.3);
                var print = sheet.getPrintSetup();
                print.setPaperSize(PrintSetup.A3_PAPERSIZE);
                print.setLandscape(true);
                print.setFitWidth((short) 1);
                print.setFitHeight((short) 1);
                sheet.getFooter().setCenter(semester.name() + " · " + name);
                workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, lastColumn, 0, 14);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "课表导出失败");
        }
    }

    private static int wrappedLines(String text) {
        int width = text.codePoints().map(code -> code > 255 ? 2 : 1).sum();
        return Math.max(1, (width + 19) / 20);
    }

    private static CellStyle style(Workbook workbook, int size, boolean bold, IndexedColors fill) {
        var font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        var style = workbook.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        style.setFillForegroundColor(fill.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void merge(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol, String text, CellStyle style) {
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) sheet.getRow(row).getCell(col).setCellStyle(style);
        }
        if (firstRow != lastRow || firstCol != lastCol) sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
        sheet.getRow(firstRow).getCell(firstCol).setCellValue(text);
    }
}
