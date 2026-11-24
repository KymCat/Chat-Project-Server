package com.project.ChatProject.service;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.AdministratorRepository;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministratorServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private AdministratorRepository administratorRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    void hasAccessReturnsTrueForVerifiedActiveAdministrator() {
        AdministratorService service = service();
        Member member = member(MemberStatus.ACTIVE, true);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(administratorRepository.existsById(MEMBER_ID))
                .thenReturn(true);

        boolean result = service.hasAccess(MEMBER_ID);

        assertThat(result).isTrue();
    }

    @Test
    void hasAccessReturnsFalseForNonAdministrator() {
        AdministratorService service = service();
        Member member = member(MemberStatus.ACTIVE, true);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(administratorRepository.existsById(MEMBER_ID))
                .thenReturn(false);

        assertThat(service.hasAccess(MEMBER_ID)).isFalse();
    }

    @Test
    void hasAccessReturnsFalseForUnverifiedAdministrator() {
        AdministratorService service = service();
        Member member = member(MemberStatus.ACTIVE, false);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(administratorRepository.existsById(MEMBER_ID))
                .thenReturn(true);

        assertThat(service.hasAccess(MEMBER_ID)).isFalse();
    }

    @Test
    void hasAccessReturnsFalseForSuspendedAdministrator() {
        AdministratorService service = service();
        Member member = member(MemberStatus.SUSPENDED, true);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(administratorRepository.existsById(MEMBER_ID))
                .thenReturn(true);

        assertThat(service.hasAccess(MEMBER_ID)).isFalse();
    }

    @Test
    void hasAccessRejectsMissingMemberBeforeAdministratorLookup() {
        AdministratorService service = service();
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> service.hasAccess(MEMBER_ID),
                ErrorCode.MEMBER_NOT_FOUND
        );
        verify(administratorRepository, never()).existsById(MEMBER_ID);
    }

    @Test
    void requireAccessAllowsVerifiedActiveAdministrator() {
        AdministratorService service = service();
        Member member = member(MemberStatus.ACTIVE, true);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(administratorRepository.existsById(MEMBER_ID))
                .thenReturn(true);

        assertThatCode(() -> service.requireAccess(MEMBER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAccessRejectsMemberWithoutAdministratorAccess() {
        AdministratorService service = service();
        Member member = member(MemberStatus.ACTIVE, true);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(administratorRepository.existsById(MEMBER_ID))
                .thenReturn(false);

        assertErrorCode(
                () -> service.requireAccess(MEMBER_ID),
                ErrorCode.ADMIN_ACCESS_DENIED
        );
    }

    private AdministratorService service() {
        return new AdministratorService(
                administratorRepository,
                memberRepository
        );
    }

    private Member member(
            MemberStatus status,
            boolean emailVerified
    ) {
        Member member = mock(Member.class);
        lenient().when(member.getStatus()).thenReturn(status);
        lenient().when(member.getEmailVerifiedAt()).thenReturn(
                emailVerified ? Instant.now() : null
        );
        return member;
    }

    private void assertErrorCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }
}
