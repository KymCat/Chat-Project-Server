package com.project.ChatProject.service;

import com.project.ChatProject.entity.StorageDeletionTask;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.StorageDeletionTaskRepository;
import com.project.ChatProject.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageDeletionServiceTest {

    private static final Long TASK_ID = 1L;
    private static final String STORAGE_KEY =
            "member-profiles/2026/10/26/profile-key";

    @Mock
    private StorageDeletionTaskRepository deletionTaskRepository;

    @Mock
    private FileStorage fileStorage;

    private StorageDeletionService storageDeletionService;

    @BeforeEach
    void setUp() {
        storageDeletionService = new StorageDeletionService(
                deletionTaskRepository,
                fileStorage
        );
    }

    @Test
    void deleteStorageObjectDeletesFileAndCompletedTask() {
        StorageDeletionTask task = StorageDeletionTask.create(STORAGE_KEY);
        when(deletionTaskRepository.findByIdForUpdate(TASK_ID))
                .thenReturn(Optional.of(task));

        storageDeletionService.deleteStorageObject(TASK_ID);

        verify(fileStorage).delete(STORAGE_KEY);
        verify(deletionTaskRepository).delete(task);
    }

    @Test
    void deleteStorageObjectReturnsWhenTaskWasAlreadyCompleted() {
        when(deletionTaskRepository.findByIdForUpdate(TASK_ID))
                .thenReturn(Optional.empty());

        storageDeletionService.deleteStorageObject(TASK_ID);

        verifyNoInteractions(fileStorage);
        verify(deletionTaskRepository, never()).delete(any());
    }

    @Test
    void deleteStorageObjectKeepsTaskWhenFileDeletionFails() {
        StorageDeletionTask task = StorageDeletionTask.create(STORAGE_KEY);
        CustomException failure =
                new CustomException(ErrorCode.STORAGE_OPERATION_FAILED);

        when(deletionTaskRepository.findByIdForUpdate(TASK_ID))
                .thenReturn(Optional.of(task));
        doThrow(failure).when(fileStorage).delete(STORAGE_KEY);

        assertThatThrownBy(() ->
                storageDeletionService.deleteStorageObject(TASK_ID)
        ).isSameAs(failure);

        verify(deletionTaskRepository, never()).delete(task);
    }
}
