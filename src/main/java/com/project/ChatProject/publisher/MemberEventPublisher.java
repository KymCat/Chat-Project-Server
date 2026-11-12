package com.project.ChatProject.publisher;

import com.project.ChatProject.dto.event.MemberEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberEventPublisher {

    private static final String MEMBER_VISIBILITY_DESTINATION =
            "/queue/member-visibility";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishHiddenEvent(
            MemberEvent event,
            Long targetMemberId
    ) {

        messagingTemplate.convertAndSendToUser(
                targetMemberId.toString(),
                MEMBER_VISIBILITY_DESTINATION,
                event
        );
    }
}
