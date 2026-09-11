package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.ChatRoomCreateResponse;
import com.project.ChatProject.dto.response.ChatRoomResponse;
import com.project.ChatProject.dto.response.GroupChatRoomResponse;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import com.project.ChatProject.repository.ChatRoomRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Transactional
    public ChatRoomCreateResponse create(Long memberId, String name) {
        Member member = findMember(memberId);
        validateMember(member);

        ChatRoom chatRoom = ChatRoom.create(name);
        ChatRoomMember chatRoomMember = ChatRoomMember.create(chatRoom, member);

        chatRoomRepository.save(chatRoom);
        chatRoomMemberRepository.save(chatRoomMember);

        return new ChatRoomCreateResponse(
                chatRoom.getId(),
                chatRoom.getType(),
                chatRoom.getName()
        );
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getChatRooms(Long memberId) {
        Member member = findMember(memberId);

        List<ChatRoomMember> lists = 
                chatRoomMemberRepository.findAllActiveByMemberId(member.getId());

        return lists.stream()
                        .map(ChatRoomResponse::of)
                        .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupChatRoomResponse> getGroupChatRooms(Long memberId) {
        Member member = findMember(memberId);
        validateMember(member);

        List<ChatRoom> lists =
                chatRoomRepository.findAllGroupChatRoom(
                        memberId,
                        ChatRoomType.GROUP
                );

        return lists.stream()
                .map(GroupChatRoomResponse::of)
                .toList();
    }

    @Transactional
    public GroupChatRoomResponse join(Long memberId, Long roomId) {
        Member member = findMember(memberId);
        validateMember(member);

        // 채팅방조회 - 삭제안됐는지도 확인해야함
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );
        validateJoinableChatRoom(chatRoom);

        Optional<ChatRoomMember> existingMember =
                chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                        chatRoom.getId(),
                        memberId
                );

        if (existingMember.isEmpty()) {
            ChatRoomMember newMember
                    = ChatRoomMember.createMember(chatRoom, member);
            chatRoomMemberRepository.save(newMember);
        }
        else
            rejoin(existingMember.get());

        return GroupChatRoomResponse.of(chatRoom);
    }

    private void validateJoinableChatRoom(ChatRoom chatRoom) {
        if (chatRoom.getDeletedAt() != null) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_DELETED
            );
        }

        if (chatRoom.getType() != ChatRoomType.GROUP) {
            throw new CustomException(
                    ErrorCode.INVALID_CHAT_ROOM_TYPE
            );
        }
    }

    private void rejoin(ChatRoomMember chatRoomMember) {
        if (chatRoomMember.isParticipating())
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_ALREADY_JOINED
            );

        chatRoomMember.rejoin();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );
    }

    private void validateMember(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.MEMBER_BLOCKED);
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.MEMBER_WITHDRAWN);
        }

        if (member.getEmailVerifiedAt() == null) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }
    }
}
