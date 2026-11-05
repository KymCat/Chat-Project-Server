package com.project.ChatProject.dto.response;

import com.project.ChatProject.dto.enums.ProfileRelationStatus;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberPublicProfileResponseTest {

    @Test
    void fromReturnsActiveMemberProfile() {
        Member member = member(
                MemberStatus.ACTIVE,
                "베리타스",
                "개발 공부 중"
        );

        MemberPublicProfileResponse response =
                MemberPublicProfileResponse.from(
                        member,
                        ProfileRelationStatus.NONE
                );

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.displayName()).isEqualTo("베리타스");
        assertThat(response.statusMessage()).isEqualTo("개발 공부 중");
        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.NONE);
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void fromMasksSuspendedMemberProfileEvenWhenNicknameMatchesDisplayName() {
        Member member = member(
                MemberStatus.SUSPENDED,
                "비활성화된 회원",
                "노출되면 안 되는 메시지"
        );

        MemberPublicProfileResponse response =
                MemberPublicProfileResponse.from(
                        member,
                        ProfileRelationStatus.ACCEPTED
                );

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.displayName()).isEqualTo("비활성화된 회원");
        assertThat(response.statusMessage()).isNull();
        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.ACCEPTED);
        assertThat(response.status()).isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    void fromMasksWithdrawnMemberProfileEvenWhenNicknameMatchesDisplayName() {
        Member member = member(
                MemberStatus.WITHDRAWN,
                "탈퇴한 유저",
                "노출되면 안 되는 메시지"
        );

        MemberPublicProfileResponse response =
                MemberPublicProfileResponse.from(
                        member,
                        ProfileRelationStatus.PENDING_SENT
                );

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.displayName()).isEqualTo("탈퇴한 유저");
        assertThat(response.statusMessage()).isNull();
        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.PENDING_SENT);
        assertThat(response.status()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    private Member member(
            MemberStatus status,
            String nickname,
            String statusMessage
    ) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(2L);
        when(member.getStatus()).thenReturn(status);
        when(member.getNickname()).thenReturn(nickname);
        when(member.getStatusMessage()).thenReturn(statusMessage);
        return member;
    }
}
