package com.n4monitor.alarm.service;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.dto.EventExportFilter;
import com.n4monitor.alarm.export.EventExcelWriter;
import com.n4monitor.tdengine.TdengineSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Orchestrates the Event page Excel export: count pre-check against the row
 * cap, then a paged TDengine read loop feeding the streaming Excel writer.
 * <p>
 * Paging keeps the memory footprint flat regardless of export size (the
 * taos-jdbcdriver REST mode buffers a whole result set in memory, so one big
 * query is not an option), and the pre-check rejects empty ranges (an empty
 * file would read like a lost export) as well as ranges above the row cap
 * instead of truncating them silently.
 * <p>
 * Failure contract: invalid input and an over-cap range raise
 * {@link IllegalArgumentException} (surfaces as 400), TDengine read failures
 * raise {@link IllegalStateException} (surfaces as 500); a query error never
 * truncates the workbook silently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventExportService {

    private final TdengineSyncService tdengineSyncService;

    /** Upper bound of data rows per export; larger ranges are rejected. */
    @Value("${alarm.export.max-rows:50000}")
    private long maxRows;

    /** Rows per TDengine page read while exporting. */
    @Value("${alarm.export.page-size:5000}")
    private int pageSize;

    /**
     * One export at a time. Paging keeps memory flat, but a large range still
     * occupies the TDengine read pool for a while; two overlapping exports
     * would multiply that load for no benefit.
     */
    private final Semaphore exportPermit = new Semaphore(1);

    /**
     * Write the filtered {@code alarm_event} rows to {@code target} as a
     * single-sheet xlsx workbook (header + one row per event, oldest first).
     *
     * @param filter validated export filter
     * @param target local file the workbook is written to; the caller owns the
     *               file lifecycle (a failure leaves a partial file behind)
     * @return the number of data rows written (excluding the header)
     * @throws IllegalArgumentException when another export is already running,
     *         the range matches no rows, or the range exceeds the configured
     *         row cap
     * @throws IllegalStateException when a TDengine read fails
     * @throws IOException when the target file cannot be written
     */
    public long writeExport(EventExportFilter filter, Path target) throws IOException {
        if (!exportPermit.tryAcquire()) {
            throw new IllegalArgumentException("Another export is already in progress, please wait");
        }
        try {
            long total = tdengineSyncService.countAlarmEvents(filter);
            if (total == 0) {
                throw new IllegalArgumentException("No rows match the selected range or filters");
            }
            if (total > maxRows) {
                throw new IllegalArgumentException(String.format(
                        "Export range too large: %d rows found, limit is %d. Narrow the time range or filters.",
                        total, maxRows));
            }
            long written = 0;
            try (EventExcelWriter writer = EventExcelWriter.create(target)) {
                int offset = 0;
                while (true) {
                    List<AlarmRecordDto> page =
                            tdengineSyncService.queryAlarmEventsPage(filter, offset, pageSize);
                    for (AlarmRecordDto row : page) {
                        writer.writeRow(row);
                    }
                    written += page.size();
                    // A short page means the range is exhausted; an exactly
                    // page-sized remainder costs one extra (empty) query only.
                    if (page.size() < pageSize) {
                        break;
                    }
                    offset += pageSize;
                }
                writer.finish();
            }
            log.info("Exported {} alarm event row(s) to {}", written, target);
            return written;
        } finally {
            exportPermit.release();
        }
    }
}
