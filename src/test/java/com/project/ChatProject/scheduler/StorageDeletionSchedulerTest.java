package com.project.ChatProject.scheduler;

import com.project.ChatProject.repository.StorageDeletionTaskRepository;
import com.project.ChatProject.service.StorageDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageDeletionSchedulerTest {

    private static final int BATCH_SIZE = 100;

    @Mock
    private StorageDeletionTaskRepository deletionTaskRepository;

    @Mock
    private StorageDeletionService storageDeletionService;

    private StorageDeletionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StorageDeletionScheduler(
                deletionTaskRepository,
                storageDeletionService
        );
        ReflectionTestUtils.setField(
                scheduler,
                "batchSize",
                BATCH_SIZE
        );
    }

    @Test
    void deleteQueuedStorageObjectsProcessesCandidateBatch() {
        List<Long> taskIds = List.of(1L, 2L, 3L);
        when(deletionTaskRepository.findDeletionCandidateIds(
                PageRequest.of(0, BATCH_SIZE)
        )).thenReturn(taskIds);

        scheduler.deleteQueuedStorageObjects();

        for (Long taskId : taskIds) {
            verify(storageDeletionService).deleteStorageObject(taskId);
        }
    }

    @Test
    void deleteQueuedStorageObjectsContinuesAfterOneFailure() {
        List<Long> taskIds = List.of(1L, 2L);
        when(deletionTaskRepository.findDeletionCandidateIds(
                PageRequest.of(0, BATCH_SIZE)
        )).thenReturn(taskIds);
        doThrow(new RuntimeException("첫 번째 삭제 실패"))
                .when(storageDeletionService)
                .deleteStorageObject(1L);

        scheduler.deleteQueuedStorageObjects();

        verify(storageDeletionService).deleteStorageObject(1L);
        verify(storageDeletionService).deleteStorageObject(2L);
    }
}
