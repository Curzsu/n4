package com.n4monitor.alarm.service;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.dto.EventExportFilter;
import com.n4monitor.tdengine.TdengineSyncService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the export orchestration: row-cap pre-check, single-flight
 * permit, paged reading loop and the produced workbook shape.
 */
@ExtendWith(MockitoExtension.class)
class EventExportServiceTest {

    @Mock
    private TdengineSyncService tdengineSyncService;

    private EventExportService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new EventExportService(tdengineSyncService);
        ReflectionTestUtils.setField(service, "maxRows", 2L);
        ReflectionTestUtils.setField(service, "pageSize", 2);
    }

    private EventExportFilter filter() {
        return new EventExportFilter(1000L, 2000L, List.of(), true);
    }

    private AlarmRecordDto row(int index) {
        AlarmRecordDto dto = new AlarmRecordDto();
        dto.setTime("2026-09-05 10:00:0" + index);
        dto.setValue(String.valueOf(index));
        dto.setSeverity("Critical");
        return dto;
    }

    @Test
    void overCapRangeIsRejectedBeforeAnyExcelWork() throws Exception {
        Path target = tempDir.resolve("out.xlsx");
        when(tdengineSyncService.countAlarmEvents(any())).thenReturn(3L);

        assertThatThrownBy(() -> service.writeExport(filter(), target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3").hasMessageContaining("2");

        verify(tdengineSyncService, never()).queryAlarmEventsPage(any(), anyInt(), anyInt());
        assertThat(target).doesNotExist();
    }

    @Test
    void emptyRangeIsRejectedBeforeAnyExcelWork() throws Exception {
        Path target = tempDir.resolve("out.xlsx");
        when(tdengineSyncService.countAlarmEvents(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.writeExport(filter(), target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No rows match");

        verify(tdengineSyncService, never()).queryAlarmEventsPage(any(), anyInt(), anyInt());
        assertThat(target).doesNotExist();
    }

    @Test
    void pagesAreReadUntilAShortPageAndWorkbookHoldsAllRows() throws Exception {
        ReflectionTestUtils.setField(service, "maxRows", 10L);
        Path target = tempDir.resolve("out.xlsx");
        when(tdengineSyncService.countAlarmEvents(any())).thenReturn(3L);
        when(tdengineSyncService.queryAlarmEventsPage(any(), eq(0), eq(2)))
                .thenReturn(List.of(row(1), row(2)));
        when(tdengineSyncService.queryAlarmEventsPage(any(), eq(2), eq(2)))
                .thenReturn(List.of(row(3)));

        long written = service.writeExport(filter(), target);

        assertThat(written).isEqualTo(3);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            Sheet sheet = workbook.getSheet("Events");
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Time");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("3");
            assertThat(sheet.getRow(4)).isNull();
        }
    }

    @Test
    void aSecondExportIsRejectedWhileOneIsRunning() throws Exception {
        // Simulate an in-flight export by holding the single permit.
        Semaphore permit = (Semaphore) ReflectionTestUtils.getField(service, "exportPermit");
        permit.acquire();
        try {
            Path target = tempDir.resolve("out.xlsx");
            assertThatThrownBy(() -> service.writeExport(filter(), target))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already in progress");
            assertThat(target).doesNotExist();
        } finally {
            permit.release();
        }
    }

    @Test
    void permitIsReleasedAfterSuccess() throws Exception {
        Path target = tempDir.resolve("out.xlsx");
        when(tdengineSyncService.countAlarmEvents(any())).thenReturn(1L);
        when(tdengineSyncService.queryAlarmEventsPage(any(), eq(0), eq(2)))
                .thenReturn(List.of(row(1)));

        service.writeExport(filter(), target);
        service.writeExport(filter(), target);

        assertThat(((Semaphore) ReflectionTestUtils.getField(service, "exportPermit"))
                .availablePermits()).isEqualTo(1);
    }
}
