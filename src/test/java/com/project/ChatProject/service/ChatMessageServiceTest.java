package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.request.ChatAttachmentMessageRequest;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.AttachmentResponse;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.ChatMessageEventType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import com.project.ChatProject.repository.ChatRoomRepository;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 10L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        ChatRoomParticipationService participation =
                new ChatRoomParticipationService(
                        memberRepository,
                        chatRoomMemberRepository,
                        chatRoomRepository
                );

        chatMessageService = new ChatMessageService(
                participation,
                chatMessageRepository,
                attachmentRepository
        );
    }

    @Test
    void saveStoresTextMessageAndUpdatesRoomLastMessageAt() {
        Instant createdAt = Instant.parse("2026-09-12T06:00:00Z");
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(sender));
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(chatRoomMember));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", 100L);
                    ReflectionTestUtils.setField(
                            message,
                            "createdAt",
                            createdAt
                    );
                    return message;
                });

        ChatMessageEvent event = chatMessageService.save(
                MEMBER_ID,
                new ChatMessageRequest(ROOM_ID, "  안녕하세요  ")
        );

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());

        ChatMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getChatRoom()).isSameAs(chatRoom);
        assertThat(savedMessage.getSender()).isSameAs(sender);
        assertThat(savedMessage.getClientMessageId()).isNotNull();
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(savedMessage.getContent()).isEqualTo("안녕하세요");
        assertThat(savedMessage.getAttachment()).isNull();
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(createdAt);

        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(event.message()).isEqualTo(
                new ChatMessageResponse(
                        100L,
                        ROOM_ID,
                        MEMBER_ID,
                        "사용자",
                        ChatMessageType.TEXT,
                        "안녕하세요",
                        null,
                        createdAt,
                        null,
                        false
                )
        );
    }

    @Test
    void saveAttachmentStoresImageMessageAndActivatesAttachment() {
        Instant createdAt = Instant.parse("2026-09-28T06:00:00Z");
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);
        Attachment attachment = attachment(
                chatRoom,
                sender,
                "image.png",
                "image/png"
        );

        stubParticipation(sender, chatRoom, chatRoomMember);
        when(attachmentRepository.findByForUpdate(200L))
                .thenReturn(Optional.of(attachment));
        stubSavedMessage(createdAt, 101L);

        ChatMessageEvent event = chatMessageService.saveAttachment(
                MEMBER_ID,
                new ChatAttachmentMessageRequest(ROOM_ID, 200L)
        );

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());

        ChatMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getChatRoom()).isSameAs(chatRoom);
        assertThat(savedMessage.getSender()).isSameAs(sender);
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.IMAGE);
        assertThat(savedMessage.getContent()).isNull();
        assertThat(savedMessage.getAttachment()).isSameAs(attachment);
        assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.ACTIVE);
        assertThat(attachment.getActivatedAt()).isNotNull();
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(createdAt);
        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(event.message().attachment()).isEqualTo(
                new AttachmentResponse(
                        200L,
                        "image.png",
                        "image/png",
                        1_024L
                )
        );
    }

    @Test
    void saveAttachmentStoresNonImageAsFileMessage() {
        Instant createdAt = Instant.parse("2026-09-28T06:00:00Z");
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);
        Attachment attachment = attachment(
                chatRoom,
                sender,
                "document.pdf",
                "application/pdf"
        );

        stubParticipation(sender, chatRoom, chatRoomMember);
        when(attachmentRepository.findByForUpdate(200L))
                .thenReturn(Optional.of(attachment));
        stubSavedMessage(createdAt, 102L);

        ChatMessageEvent event = chatMessageService.saveAttachment(
                MEMBER_ID,
                new ChatAttachmentMessageRequest(ROOM_ID, 200L)
        );

        assertThat(event.message().type()).isEqualTo(ChatMessageType.FILE);
        assertThat(event.message().attachment().originalName())
                .isEqualTo("document.pdf");
    }

    @Test
    void saveAttachmentRejectsUnknownAttachment() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);

        stubParticipation(sender, chatRoom, chatRoomMember);
        when(attachmentRepository.findByForUpdate(200L))
                .thenReturn(Optional.empty());

        assertAttachmentSaveRejected(ErrorCode.ATTACHMENT_NOT_FOUND);
    }

    @Test
    void saveAttachmentRejectsAttachmentOwnedByAnotherMember() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        Member otherMember = member(MemberStatus.ACTIVE, Instant.now());
        ReflectionTestUtils.setField(otherMember, "id", 2L);
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);
        Attachment attachment = attachment(
                chatRoom,
                otherMember,
                "image.png",
                "image/png"
        );

        stubParticipation(sender, chatRoom, chatRoomMember);
        when(attachmentRepository.findByForUpdate(200L))
                .thenReturn(Optional.of(attachment));

        assertAttachmentSaveRejected(ErrorCode.ATTACHMENT_NOT_FOUND);
        assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.PENDING);
    }

    @Test
    void saveAttachmentRejectsAttachmentUploadedToAnotherRoom() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom requestedRoom = chatRoom();
        ChatRoom anotherRoom = ChatRoom.create("다른 채팅방");
        ReflectionTestUtils.setField(anotherRoom, "id", 20L);
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(requestedRoom, sender);
        Attachment attachment = attachment(
                anotherRoom,
                sender,
                "image.png",
                "image/png"
        );

        stubParticipation(sender, requestedRoom, chatRoomMember);
        when(attachmentRepository.findByForUpdate(200L))
                .thenReturn(Optional.of(attachment));

        assertAttachmentSaveRejected(ErrorCode.ATTACHMENT_NOT_FOUND);

        assertThat(attachment.getStatus())
                .isEqualTo(AttachmentStatus.PENDING);
        assertThat(attachment.getActivatedAt()).isNull();
    }

    @Test
    void saveAttachmentRejectsAlreadyUsedAttachment() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);
        Attachment attachment = attachment(
                chatRoom,
                sender,
                "image.png",
                "image/png"
        );
        attachment.activate();

        stubParticipation(sender, chatRoom, chatRoomMember);
        when(attachmentRepository.findByForUpdate(200L))
                .thenReturn(Optional.of(attachment));

        assertAttachmentSaveRejected(ErrorCode.ATTACHMENT_ALREADY_USED);
    }

    @Test
    void saveRejectsUnknownMember() {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom()));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertSaveRejected(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void saveRejectsSuspendedMember() {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom()));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(MemberStatus.SUSPENDED, Instant.now())
                ));

        assertSaveRejected(ErrorCode.MEMBER_BLOCKED);
    }

    @Test
    void saveRejectsWithdrawnMember() {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom()));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(MemberStatus.WITHDRAWN, Instant.now())
                ));

        assertSaveRejected(ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    void saveRejectsMemberWhoseEmailIsNotVerified() {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom()));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(MemberStatus.ACTIVE, null)
                ));

        assertSaveRejected(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }

    @Test
    void saveRejectsUnknownChatRoom() {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.empty());

        assertSaveRejected(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void saveRejectsDeletedChatRoom() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ReflectionTestUtils.setField(
                chatRoom,
                "deletedAt",
                Instant.now()
        );

        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));

        assertSaveRejected(ErrorCode.CHAT_ROOM_DELETED);
    }

    @Test
    void saveRejectsMemberWhoHasNotJoinedRoom() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(sender));
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.empty());

        assertSaveRejected(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }

    @Test
    void saveRejectsMemberWhoHasLeftRoom() {
        Member sender = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, sender);
        ReflectionTestUtils.setField(
                chatRoomMember,
                "leftAt",
                Instant.now()
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(sender));
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(chatRoomMember));

        assertSaveRejected(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }

    private void assertSaveRejected(ErrorCode expectedErrorCode) {
        assertThatThrownBy(() -> chatMessageService.save(
                MEMBER_ID,
                new ChatMessageRequest(ROOM_ID, "안녕하세요")
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode)
        );

        verify(chatMessageRepository, never())
                .save(any(ChatMessage.class));
    }

    private void assertAttachmentSaveRejected(
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() -> chatMessageService.saveAttachment(
                MEMBER_ID,
                new ChatAttachmentMessageRequest(ROOM_ID, 200L)
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode)
        );

        verify(chatMessageRepository, never())
                .save(any(ChatMessage.class));
    }

    private void stubParticipation(
            Member member,
            ChatRoom chatRoom,
            ChatRoomMember chatRoomMember
    ) {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(chatRoomMember));
    }

    private void stubSavedMessage(
            Instant createdAt,
            Long messageId
    ) {
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", messageId);
                    ReflectionTestUtils.setField(
                            message,
                            "createdAt",
                            createdAt
                    );
                    return message;
                });
    }

    private Attachment attachment(
            ChatRoom chatRoom,
            Member uploader,
            String originalName,
            String contentType
    ) {
        Attachment attachment = Attachment.createPending(
                uploader,
                chatRoom,
                originalName,
                contentType,
                1_024L,
                "2026/09/28/test-file"
        );
        ReflectionTestUtils.setField(attachment, "id", 200L);
        return attachment;
    }

    private Member member(
            MemberStatus status,
            Instant emailVerifiedAt
    ) {
        Member member = Member.create(
                null,
                "user@example.com",
                "사용자"
        );
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(
                member,
                "emailVerifiedAt",
                emailVerifiedAt
        );
        return member;
    }

    private ChatRoom chatRoom() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ReflectionTestUtils.setField(chatRoom, "id", ROOM_ID);
        return chatRoom;
    }
}
