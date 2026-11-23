package io.aidlink.interfaces.api;

import io.aidlink.collector.infrastructure.batch.Gov24DailySync;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only operational status; no credentials, source payloads, or trigger operation. */
@RestController
@Profile("postgres")
public class Gov24SyncStatusController {
    private final Gov24DailySync dailySync;

    public Gov24SyncStatusController(Gov24DailySync dailySync) {
        this.dailySync = dailySync;
    }

    @GetMapping("/api/v1/collections/gov24/status")
    public Gov24DailySync.SyncStatus status() {
        return dailySync.status();
    }
}
