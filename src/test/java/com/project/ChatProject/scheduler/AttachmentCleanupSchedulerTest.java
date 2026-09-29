package com.project.ChatProject.scheduler;

import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.service.AttachmentCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentCleanupSchedulerTest {

    private static final long RETENTION_HOURS = 24L;
    private static final int BATCH_SIZE = 100;

    @Mock
    private AttachmentCleanupService attachmentCleanupService;

    @Mock
    private AttachmentRepository attachmentRepository;

    private AttachmentCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AttachmentCleanupScheduler(
                attachmentCleanupService,
                attachmentRepository
        );
        ReflectionTestUtils.setField(
                scheduler,
                "pendingRetentionHours",
                RETENTION_HOURS
        );
        ReflectionTestUtils.setField(
                scheduler,
                "batchSize",
                BATCH_SIZE
        );
    }

    @Test
    void cleanupPendingAttachmentsFindsAndCleansCandidates() {
        List<Long> attachmentIds = List.of(1L, 2L, 3L);
        when(attachmentRepository.findCleanupCandidateIds(
                org.mockito.ArgumentMatchers.eq(AttachmentStatus.PENDING),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq(
                        PageRequest.of(0, BATCH_SIZE)
                )
        )).thenReturn(attachmentIds);
        Instant startedAt = Instant.now();

        scheduler.cleanupPendingAttachments();

        Instant finishedAt = Instant.now();
        ArgumentCaptor<Instant> cutoffCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(attachmentRepository).findCleanupCandidateIds(
                org.mockito.ArgumentMatchers.eq(AttachmentStatus.PENDING),
                cutoffCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(
                        PageRequest.of(0, BATCH_SIZE)
                )
        );

        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff)
                .isAfterOrEqualTo(
                        startedAt.minus(RETENTION_HOURS, ChronoUnit.HOURS)
                )
                .isBeforeOrEqualTo(
                        finishedAt.minus(RETENTION_HOURS, ChronoUnit.HOURS)
                );
        for (Long attachmentId : attachmentIds) {
            verify(attachmentCleanupService)
                    .cleanupPendingAttachment(attachmentId, cutoff);
        }
    }

    @Test
    void cleanupPendingAttachmentsContinuesWhenOneCandidateFails() {
        List<Long> attachmentIds = List.of(1L, 2L);
        when(attachmentRepository.findCleanupCandidateIds(
                org.mockito.ArgumentMatchers.eq(AttachmentStatus.PENDING),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq(
                        PageRequest.of(0, BATCH_SIZE)
                )
        )).thenReturn(attachmentIds);
        doThrow(new RuntimeException("첫 번째 정리 실패"))
                .when(attachmentCleanupService)
                .cleanupPendingAttachment(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.any(Instant.class)
                );

        scheduler.cleanupPendingAttachments();

        ArgumentCaptor<Instant> cutoffCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(attachmentCleanupService)
                .cleanupPendingAttachment(
                        org.mockito.ArgumentMatchers.eq(1L),
                        cutoffCaptor.capture()
                );
        verify(attachmentCleanupService)
                .cleanupPendingAttachment(
                        2L,
                        cutoffCaptor.getValue()
                );
    }
}
