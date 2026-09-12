package com.project.ChatProject.repository;

import com.project.ChatProject.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long>
{
}
