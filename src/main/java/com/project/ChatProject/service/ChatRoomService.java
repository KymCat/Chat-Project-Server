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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Transactional
    public ChatRoomCreateResponse create(Long memberId, String name) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );
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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        List<ChatRoomMember> lists = 
                chatRoomMemberRepository.findAllActiveByMemberId(member.getId());

        return lists.stream()
                        .map(ChatRoomResponse::of)
                        .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupChatRoomResponse> getGroupChatRooms(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.MEMBER_NOT_FOUND)
                );

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
