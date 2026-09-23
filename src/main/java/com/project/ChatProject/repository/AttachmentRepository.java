package com.project.ChatProject.repository;

import com.project.ChatProject.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository
        extends JpaRepository<Attachment, Long> {
}
