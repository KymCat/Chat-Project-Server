package com.project.ChatProject.publisher;

import com.project.ChatProject.dto.event.MemberEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MemberEventPublisher memberEventPublisher;

    @Test
    void publishHiddenEventSendsBlockerIdToBlockedMemberQueue() {
        MemberEvent event = MemberEvent.hidden(1L);

        memberEventPublisher.publishHiddenEvent(event, 2L);

        verify(messagingTemplate).convertAndSendToUser(
                "2",
                "/queue/member-visibility",
                event
        );
    }

    @Test
    void publishHiddenEventSendsUnblockedEventToPreviouslyBlockedMemberQueue() {
        // 추가: 차단과 같은 개인 queue로 차단 해제 이벤트를 전달한다.
        MemberEvent event = MemberEvent.unhidden(1L);

        memberEventPublisher.publishHiddenEvent(event, 2L);

        verify(messagingTemplate).convertAndSendToUser(
                "2",
                "/queue/member-visibility",
                event
        );
    }
}
