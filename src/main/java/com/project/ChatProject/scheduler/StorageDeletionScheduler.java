package com.project.ChatProject.scheduler;

import com.project.ChatProject.repository.StorageDeletionTaskRepository;
import com.project.ChatProject.service.StorageDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageDeletionScheduler {

    private final StorageDeletionTaskRepository deletionTaskRepository;
    private final StorageDeletionService storageDeletionService;

    @Value("${chat.storage.deletion.batch-size:100}")
    private int batchSize;


    // 기본값은 매분 0초에 실행
    @Scheduled(
            cron = "${chat.storage.deletion.cron:0 * * * * *}",
            zone = "UTC"
    )
    public void deleteQueuedStorageObjects() {
        List<Long> taskIds =
                deletionTaskRepository.findDeletionCandidateIds(
                        PageRequest.of(0, batchSize)
                );

        for (Long taskId : taskIds) {
            try {
                storageDeletionService.deleteStorageObject(taskId);
            } catch (RuntimeException exception) {

                // 하나의 객체 삭제가 실패하더라도 멈추지않고 다음을 진행하도록 처리
                log.error(
                        "Storage object deletion failed, taskId={}",
                        taskId,
                        exception
                );
            }
        }
    }
}