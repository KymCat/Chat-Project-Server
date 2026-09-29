package com.project.ChatProject.scheduler;

import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.service.AttachmentCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupScheduler {

    private final AttachmentCleanupService attachmentCleanupService;
    private final AttachmentRepository attachmentRepository;

    @Value("${chat.storage.cleanup.pending-retention-hours:24}")
    private long pendingRetentionHours;

    @Value("${chat.storage.cleanup.batch-size:100}")
    private int batchSize;

    @Scheduled(
            cron = "${chat.storage.cleanup.cron:0 0 * * * *}",
            zone = "UTC"
    )
    public void cleanupPendingAttachments() {
        Instant cutoff = Instant.now()
                .minus(pendingRetentionHours, ChronoUnit.HOURS);

        List<Long> attachmentIds =
                attachmentRepository.findCleanupCandidateIds(
                        AttachmentStatus.PENDING,
                        cutoff,
                        PageRequest.of(0, batchSize)
                );

        for (Long attachmentId : attachmentIds) {
            try {
                attachmentCleanupService
                        .cleanupPendingAttachment(attachmentId, cutoff);
            } catch (RuntimeException exception) {
                log.error(
                        "Pending attachment cleanup failed, attachmentId={}",
                        attachmentId,
                        exception
                );
            }
        }

    }
}
