package com.project.ChatProject.service;

import com.project.ChatProject.entity.StorageDeletionTask;
import com.project.ChatProject.repository.StorageDeletionTaskRepository;
import com.project.ChatProject.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorageDeletionService {

    private final StorageDeletionTaskRepository deletionTaskRepository;
    private final FileStorage fileStorage;

    @Transactional
    public void deleteStorageObject(Long taskId) {
        StorageDeletionTask task = deletionTaskRepository
                .findByIdForUpdate(taskId)
                .orElse(null);

        // 이미 완료된 작업이라면 종료처리 => 멱등성
        if (task == null)
            return;

        fileStorage.delete(task.getStorageKey());
        deletionTaskRepository.delete(task);
    }
}
