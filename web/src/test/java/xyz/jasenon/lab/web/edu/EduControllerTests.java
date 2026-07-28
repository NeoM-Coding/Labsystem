package xyz.jasenon.lab.web.edu;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.edu.api.SemesterService;
import xyz.jasenon.lab.edu.api.TimetableService;
import xyz.jasenon.lab.edu.api.command.SemesterUpdate;
import xyz.jasenon.lab.edu.api.command.TimetableUpdate;
import xyz.jasenon.lab.edu.api.model.WeekType;
import xyz.jasenon.lab.edu.api.view.SemesterView;
import xyz.jasenon.lab.edu.api.view.TimetableImportResult;
import xyz.jasenon.lab.edu.api.view.TimetableView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EduControllerTests {

    @Test
    void semesterPathIdOverridesBodyId() {
        SemesterService service = mock(SemesterService.class);
        SemesterView view = new SemesterView(
                "semester-path", "2026-2027 第1学期",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 20), null, null
        );
        when(service.update(org.mockito.ArgumentMatchers.any())).thenReturn(RpcResult.success(view));
        SemesterController controller = new SemesterController();
        ReflectionTestUtils.setField(controller, "semesterService", service);
        controller.update("semester-path", new SemesterUpdate(
                "semester-body", view.name(), view.startDate(), view.endDate()
        ));
        verify(service).update(argThat(command -> command.semesterId().equals("semester-path")));
    }

    @Test
    void timetablePathIdOverridesBodyId() {
        TimetableService service = mock(TimetableService.class);
        TimetableView view = new TimetableView(
                "path-id", "semester-1", null, "lab-1", "实验室一",
                "软件工程", "张三", WeekType.Both, 1, 16,
                LocalTime.of(8, 0), LocalTime.of(9, 40), 1
        );
        when(service.update(org.mockito.ArgumentMatchers.any())).thenReturn(RpcResult.success(view));
        TimetableController controller = new TimetableController(1024);
        ReflectionTestUtils.setField(controller, "timetableService", service);
        TimetableUpdate body = new TimetableUpdate(
                "body-id", "semester-1", "lab-1", "软件工程", "张三",
                WeekType.Both, 1, 16, LocalTime.of(8, 0), LocalTime.of(9, 40), 1
        );
        controller.update("path-id", body);
        verify(service).update(argThat(command -> command.timetableId().equals("path-id")));
    }

    @Test
    void multipartIsConvertedToSerializableRpcCommand() {
        TimetableService service = mock(TimetableService.class);
        when(service.importExcel(org.mockito.ArgumentMatchers.any()))
                .thenReturn(RpcResult.success(new TimetableImportResult(0, 0, List.of())));
        TimetableController controller = new TimetableController(1024);
        ReflectionTestUtils.setField(controller, "timetableService", service);
        byte[] content = new byte[]{'P', 'K', 1, 2};
        MockMultipartFile file = new MockMultipartFile(
                "excel", "schedule.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content
        );
        controller.importExcel(file, "semester-1", "lab-1");
        verify(service).importExcel(argThat(command ->
                command.semesterId().equals("semester-1")
                        && command.laboratoryId().equals("lab-1")
                        && java.util.Arrays.equals(command.content(), content)
        ));
    }

    @Test
    void multipartSizeIsRejectedBeforeRpc() {
        TimetableController controller = new TimetableController(3);
        MockMultipartFile file = new MockMultipartFile(
                "excel", "schedule.xlsx", "application/octet-stream", new byte[]{1, 2, 3, 4}
        );
        assertThrows(BusinessException.class,
                () -> controller.importExcel(file, "semester-1", "lab-1"));
    }
}
