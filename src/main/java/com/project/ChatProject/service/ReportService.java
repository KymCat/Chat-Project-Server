package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.MessageReportListResponse;
import com.project.ChatProject.entity.MessageReport;
import com.project.ChatProject.repository.MessageReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int PAGE_SIZE = 20;

    private final AdministratorService administratorService;
    private final MessageReportRepository messageReportRepository;

    @Transactional(readOnly = true)
    public PagedModel<MessageReportListResponse> getMessageReport(
            Long memberId,
            int page
    ) {

        administratorService.requireAccess(memberId);

        Page<MessageReport> messageReports = messageReportRepository
                .findAllMessageReport(
                        PageRequest.of(page, PAGE_SIZE)
                );

        return new PagedModel<>(
                messageReports
                        .map(MessageReportListResponse::from)
        );
    }
}
