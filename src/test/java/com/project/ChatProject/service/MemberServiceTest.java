package com.project.ChatProject.service;

import com.project.ChatProject.dto.enums.ProfileRelationStatus;
import com.project.ChatProject.dto.request.SocialProfileProviderRequest;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.MemberPublicProfileResponse;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.StorageDeletionTask;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.profile.ProfileImageValidator;
import com.project.ChatProject.profile.ValidatedProfileImage;
import com.project.ChatProject.profile.google.DownloadedProfileImage;
import com.project.ChatProject.profile.google.GoogleProfileImageClient;
import com.project.ChatProject.repository.FriendshipRepository;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.repository.SocialAccountRepository;
import com.project.ChatProject.repository.StorageDeletionTaskRepository;
import com.project.ChatProject.storage.FileStorage;
import com.project.ChatProject.storage.StorageCategory;
import com.project.ChatProject.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long MEMBER_ID = 2L;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberCredentialRepository memberCredentialRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ProfileImageValidator profileImageValidator;

    @Mock
    private GoogleProfileImageClient googleProfileImageClient;

    @Mock
    private StorageDeletionTaskRepository storageDeletionTaskRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void getProfileReturnsAuthenticatedMemberInformation() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("베리타스");
        when(member.getEmail()).thenReturn("veritas@example.com");
        when(member.getStatusMessage()).thenReturn("개발 공부 중");
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.hasStoredProfileImage()).thenReturn(false);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        MemberProfileResponse response =
                memberService.getProfile(MEMBER_ID);

        assertThat(response.nickname()).isEqualTo("베리타스");
        assertThat(response.email()).isEqualTo("veritas@example.com");
        assertThat(response.statusMessage()).isEqualTo("개발 공부 중");
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.usesStoredProfileImage()).isFalse();
    }

    @Test
    void getProfileRejectsUnknownMember() {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getProfile(999L))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );
    }

    @Test
    void getPublicProfileReturnsSelfWithoutLookingUpFriendship() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getNickname()).thenReturn("베리타스");
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        MemberPublicProfileResponse response =
                memberService.getPublicProfile(MEMBER_ID, MEMBER_ID);

        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.SELF);
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void getPublicProfileReturnsNoneWithoutFriendship() {
        Long targetMemberId = 3L;
        Member member = activeAuthenticatedMember();
        Member targetMember = activePublicMember(targetMemberId, "상대방");
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(memberRepository.findById(targetMemberId))
                .thenReturn(Optional.of(targetMember));
        when(friendshipRepository.findBetweenMembers(
                MEMBER_ID,
                targetMemberId
        )).thenReturn(Optional.empty());

        MemberPublicProfileResponse response =
                memberService.getPublicProfile(MEMBER_ID, targetMemberId);

        assertThat(response.memberId()).isEqualTo(targetMemberId);
        assertThat(response.displayName()).isEqualTo("상대방");
        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.NONE);
    }

    @Test
    void getPublicProfileReturnsAcceptedFriendship() {
        Long targetMemberId = 3L;
        Member member = activeAuthenticatedMember();
        Member targetMember = activePublicMember(targetMemberId, "상대방");
        Friendship friendship = mock(Friendship.class);
        when(friendship.getStatus()).thenReturn(FriendshipStatus.ACCEPTED);
        mockPublicProfileLookup(member, targetMember, friendship);

        MemberPublicProfileResponse response =
                memberService.getPublicProfile(MEMBER_ID, targetMemberId);

        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.ACCEPTED);
    }

    @Test
    void getPublicProfileReturnsPendingSentByAuthenticatedMember() {
        Long targetMemberId = 3L;
        Member member = activeAuthenticatedMember();
        Member targetMember = activePublicMember(targetMemberId, "상대방");
        Friendship friendship = mock(Friendship.class);
        when(friendship.getStatus()).thenReturn(FriendshipStatus.PENDING);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(friendship.getRequester()).thenReturn(member);
        mockPublicProfileLookup(member, targetMember, friendship);

        MemberPublicProfileResponse response =
                memberService.getPublicProfile(MEMBER_ID, targetMemberId);

        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.PENDING_SENT);
    }

    @Test
    void getPublicProfileReturnsPendingReceivedByAuthenticatedMember() {
        Long targetMemberId = 3L;
        Member member = activeAuthenticatedMember();
        Member targetMember = activePublicMember(targetMemberId, "상대방");
        Friendship friendship = mock(Friendship.class);
        when(friendship.getStatus()).thenReturn(FriendshipStatus.PENDING);
        when(friendship.getRequester()).thenReturn(targetMember);
        mockPublicProfileLookup(member, targetMember, friendship);

        MemberPublicProfileResponse response =
                memberService.getPublicProfile(MEMBER_ID, targetMemberId);

        assertThat(response.relationStatus())
                .isEqualTo(ProfileRelationStatus.PENDING_RECEIVED);
    }

    @Test
    void getPublicProfileRejectsUnknownTargetMember() {
        Long targetMemberId = 999L;
        Member member = activeAuthenticatedMember();
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(memberRepository.findById(targetMemberId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.getPublicProfile(MEMBER_ID, targetMemberId)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
        );

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void updateStatusMessageChangesMembersStatusMessage() {
        Member member = Member.create(
                "veritas@example.com",
                "베리타스"
        );
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        memberService.updateStatusMessage(
                MEMBER_ID,
                "개발 공부 중"
        );

        assertThat(member.getStatusMessage()).isEqualTo("개발 공부 중");
    }

    @Test
    void updateStatusMessageClearsStatusMessageWithNull() {
        Member member = Member.create(
                "veritas@example.com",
                "베리타스"
        );
        member.updateStatusMessage("기존 상태 메시지");
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        memberService.updateStatusMessage(MEMBER_ID, null);

        assertThat(member.getStatusMessage()).isNull();
    }

    @Test
    void updateStatusMessageRejectsUnknownMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateStatusMessage(
                MEMBER_ID,
                "개발 공부 중"
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
        );
    }

    @Test
    void getProfileImageLoadsStoredImageWithDatabaseMetadata() {
        Member member = mock(Member.class);
        Resource resource = new ByteArrayResource("image".getBytes());
        String storageKey = "member-profiles/2026/10/26/profile-key";

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.hasStoredProfileImage()).thenReturn(true);
        when(member.getProfileImageStorageKey()).thenReturn(storageKey);
        when(member.getProfileImageContentType()).thenReturn("image/png");
        when(member.getProfileImageSizeBytes()).thenReturn(5L);
        when(fileStorage.load(storageKey)).thenReturn(resource);

        ProfileImageResult result =
                memberService.getProfileImage(MEMBER_ID);

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.sizeBytes()).isEqualTo(5L);
    }

    @Test
    void getProfileImageRejectsMemberWithoutProfileImage() {
        Member member = mock(Member.class);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);

        assertThatThrownBy(() ->
                memberService.getProfileImage(MEMBER_ID)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PROFILE_IMAGE_NOT_FOUND)
        );

        verifyNoInteractions(fileStorage);
    }

    @Test
    void getProfileImageRejectsSuspendedMember() {
        assertInactiveMemberProfileImageRejected(MemberStatus.SUSPENDED);
    }

    @Test
    void getProfileImageRejectsWithdrawnMember() {
        assertInactiveMemberProfileImageRejected(MemberStatus.WITHDRAWN);
    }

    @Test
    void getProfileImageRejectsUnknownMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.getProfileImage(MEMBER_ID)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
        );

        verifyNoInteractions(fileStorage);
    }

    @Test
    void applySocialProfileImageQueuesStoredImageDeletion() {
        Member member = mock(Member.class);
        SocialAccount account = mock(SocialAccount.class);
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);
        String previousStorageKey =
                "member-profiles/2026/10/27/old-key";
        String socialImageUrl =
                "https://lh3.googleusercontent.com/profile.png";
        byte[] content = {1, 2, 3};
        DownloadedProfileImage downloaded =
                new DownloadedProfileImage(content);
        ValidatedProfileImage validated =
                new ValidatedProfileImage("image/jpeg");
        StoredFile storedFile = new StoredFile(
                "member-profiles/2026/11/06/new-key",
                "google-profile-image",
                "image/jpeg",
                content.length
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndMemberId(
                SocialProvider.GOOGLE,
                MEMBER_ID
        )).thenReturn(Optional.of(account));
        when(account.getProviderProfileImageUrl())
                .thenReturn(socialImageUrl);
        when(member.getProfileImageStorageKey())
                .thenReturn(previousStorageKey);
        when(googleProfileImageClient.download(socialImageUrl))
                .thenReturn(downloaded);
        when(profileImageValidator.validate(content))
                .thenReturn(validated);
        when(fileStorage.store(
                content,
                "google-profile-image",
                "image/jpeg",
                StorageCategory.MEMBER_PROFILE
        )).thenReturn(storedFile);

        memberService.applySocialProfileImage(MEMBER_ID, request);

        verify(member).updateProfileImage(
                storedFile.storageKey(),
                "image/jpeg",
                storedFile.sizeBytes()
        );
        verify(memberRepository).saveAndFlush(member);
        verify(fileStorage, never()).delete(storedFile.storageKey());

        ArgumentCaptor<StorageDeletionTask> taskCaptor =
                ArgumentCaptor.forClass(StorageDeletionTask.class);
        verify(storageDeletionTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStorageKey())
                .isEqualTo(previousStorageKey);
    }

    @Test
    void applySocialProfileImageDoesNotQueueDeletionWithoutStoredImage() {
        Member member = mock(Member.class);
        SocialAccount account = mock(SocialAccount.class);
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);
        String socialImageUrl =
                "https://lh3.googleusercontent.com/profile.png";
        byte[] content = {1, 2, 3};
        DownloadedProfileImage downloaded =
                new DownloadedProfileImage(content);
        ValidatedProfileImage validated =
                new ValidatedProfileImage("image/jpeg");
        StoredFile storedFile = new StoredFile(
                "member-profiles/2026/11/06/new-key",
                "google-profile-image",
                "image/jpeg",
                content.length
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndMemberId(
                SocialProvider.GOOGLE,
                MEMBER_ID
        )).thenReturn(Optional.of(account));
        when(account.getProviderProfileImageUrl())
                .thenReturn(socialImageUrl);
        when(googleProfileImageClient.download(socialImageUrl))
                .thenReturn(downloaded);
        when(profileImageValidator.validate(content))
                .thenReturn(validated);
        when(fileStorage.store(
                content,
                "google-profile-image",
                "image/jpeg",
                StorageCategory.MEMBER_PROFILE
        )).thenReturn(storedFile);

        memberService.applySocialProfileImage(MEMBER_ID, request);

        verify(member).updateProfileImage(
                storedFile.storageKey(),
                "image/jpeg",
                storedFile.sizeBytes()
        );
        verify(memberRepository).saveAndFlush(member);
        verifyNoInteractions(storageDeletionTaskRepository);
        verify(fileStorage, never()).delete(storedFile.storageKey());
    }

    @Test
    void applySocialProfileImageRejectsMissingSocialAccount() {
        Member member = mock(Member.class);
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndMemberId(
                SocialProvider.GOOGLE,
                MEMBER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.applySocialProfileImage(MEMBER_ID, request)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND)
        );

        verify(memberRepository, never()).saveAndFlush(member);
        verifyNoInteractions(
                googleProfileImageClient,
                profileImageValidator,
                storageDeletionTaskRepository,
                fileStorage
        );
    }

    @Test
    void applySocialProfileImageRejectsMissingSocialProfileImage() {
        Member member = mock(Member.class);
        SocialAccount account = mock(SocialAccount.class);
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndMemberId(
                SocialProvider.GOOGLE,
                MEMBER_ID
        )).thenReturn(Optional.of(account));

        assertThatThrownBy(() ->
                memberService.applySocialProfileImage(MEMBER_ID, request)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PROFILE_IMAGE_NOT_FOUND)
        );

        verify(memberRepository, never()).saveAndFlush(member);
        verifyNoInteractions(
                googleProfileImageClient,
                profileImageValidator,
                storageDeletionTaskRepository,
                fileStorage
        );
    }

    @Test
    void applySocialProfileImageStopsWhenDownloadFails() {
        Member member = mock(Member.class);
        SocialAccount account = mock(SocialAccount.class);
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);
        String socialImageUrl =
                "https://lh3.googleusercontent.com/profile.png";
        CustomException failure = new CustomException(
                ErrorCode.SOCIAL_PROFILE_IMAGE_DOWNLOAD_FAILED
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndMemberId(
                SocialProvider.GOOGLE,
                MEMBER_ID
        )).thenReturn(Optional.of(account));
        when(account.getProviderProfileImageUrl())
                .thenReturn(socialImageUrl);
        when(googleProfileImageClient.download(socialImageUrl))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                memberService.applySocialProfileImage(MEMBER_ID, request)
        ).isSameAs(failure);

        verifyNoInteractions(
                profileImageValidator,
                fileStorage,
                storageDeletionTaskRepository
        );
        verify(memberRepository, never()).saveAndFlush(member);
    }

    @Test
    void applySocialProfileImageStopsWhenValidationFails() {
        Member member = mock(Member.class);
        SocialAccount account = mock(SocialAccount.class);
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);
        String socialImageUrl =
                "https://lh3.googleusercontent.com/profile.png";
        byte[] content = {1, 2, 3};
        DownloadedProfileImage downloaded =
                new DownloadedProfileImage(content);
        CustomException failure =
                new CustomException(ErrorCode.PROFILE_IMAGE_INVALID);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndMemberId(
                SocialProvider.GOOGLE,
                MEMBER_ID
        )).thenReturn(Optional.of(account));
        when(account.getProviderProfileImageUrl())
                .thenReturn(socialImageUrl);
        when(googleProfileImageClient.download(socialImageUrl))
                .thenReturn(downloaded);
        when(profileImageValidator.validate(content))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                memberService.applySocialProfileImage(MEMBER_ID, request)
        ).isSameAs(failure);

        verifyNoInteractions(fileStorage, storageDeletionTaskRepository);
        verify(memberRepository, never()).saveAndFlush(member);
    }

    @Test
    void initializeSocialProfileImageStoresImageWhenAbsent() {
        Member member = mock(Member.class);
        String socialImageUrl =
                "https://lh3.googleusercontent.com/profile.png";
        byte[] content = {1, 2, 3};
        DownloadedProfileImage downloaded =
                new DownloadedProfileImage(content);
        ValidatedProfileImage validated =
                new ValidatedProfileImage("image/jpeg");
        StoredFile storedFile = new StoredFile(
                "member-profiles/2026/11/06/new-key",
                "google-profile-image",
                "image/jpeg",
                content.length
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(googleProfileImageClient.download(socialImageUrl))
                .thenReturn(downloaded);
        when(profileImageValidator.validate(content))
                .thenReturn(validated);
        when(fileStorage.store(
                content,
                "google-profile-image",
                "image/jpeg",
                StorageCategory.MEMBER_PROFILE
        )).thenReturn(storedFile);

        memberService.initializeSocialProfileImageIfAbsent(
                MEMBER_ID,
                socialImageUrl
        );

        verify(member).updateProfileImage(
                storedFile.storageKey(),
                "image/jpeg",
                storedFile.sizeBytes()
        );
        verify(memberRepository).saveAndFlush(member);
        verifyNoInteractions(storageDeletionTaskRepository);
    }

    @Test
    void initializeSocialProfileImagePreservesStoredImage() {
        Member member = mock(Member.class);
        when(member.hasStoredProfileImage()).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        memberService.initializeSocialProfileImageIfAbsent(
                MEMBER_ID,
                "https://lh3.googleusercontent.com/profile.png"
        );

        verifyNoInteractions(
                googleProfileImageClient,
                profileImageValidator,
                fileStorage,
                storageDeletionTaskRepository
        );
        verify(memberRepository, never()).saveAndFlush(member);
    }

    @Test
    void initializeSocialProfileImageIgnoresMissingImageUrl() {
        Member member = mock(Member.class);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        memberService.initializeSocialProfileImageIfAbsent(
                MEMBER_ID,
                null
        );

        verifyNoInteractions(
                googleProfileImageClient,
                profileImageValidator,
                fileStorage,
                storageDeletionTaskRepository
        );
        verify(memberRepository, never()).saveAndFlush(member);
    }

    @Test
    void profileUploadStoresValidatedImageAndQueuesPreviousImageDeletion() {
        Member member = mock(Member.class);
        MockMultipartFile file = profileFile();
        String previousStorageKey =
                "member-profiles/2026/10/25/old-key";
        StoredFile storedFile = storedProfile();

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(member.getProfileImageStorageKey())
                .thenReturn(previousStorageKey);
        when(profileImageValidator.validate(file))
                .thenReturn(new ValidatedProfileImage("image/png"));
        when(fileStorage.store(file, StorageCategory.MEMBER_PROFILE))
                .thenReturn(storedFile);

        memberService.profileUpload(MEMBER_ID, file);

        verify(member).updateProfileImage(
                storedFile.storageKey(),
                "image/png",
                storedFile.sizeBytes()
        );
        verify(memberRepository).saveAndFlush(member);

        ArgumentCaptor<StorageDeletionTask> taskCaptor =
                ArgumentCaptor.forClass(StorageDeletionTask.class);
        verify(storageDeletionTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStorageKey())
                .isEqualTo(previousStorageKey);
    }

    @Test
    void profileUploadStopsBeforeStorageWhenValidationFails() {
        Member member = mock(Member.class);
        MockMultipartFile file = profileFile();
        CustomException failure =
                new CustomException(ErrorCode.PROFILE_IMAGE_INVALID);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(profileImageValidator.validate(file)).thenThrow(failure);

        assertThatThrownBy(() ->
                memberService.profileUpload(MEMBER_ID, file)
        ).isSameAs(failure);

        verifyNoInteractions(fileStorage, storageDeletionTaskRepository);
        verify(memberRepository, never()).saveAndFlush(member);
    }

    @Test
    void profileUploadDeletesNewImageWhenDatabaseSaveFails() {
        Member member = mock(Member.class);
        MockMultipartFile file = profileFile();
        StoredFile storedFile = storedProfile();
        RuntimeException databaseFailure =
                new RuntimeException("DB 저장 실패");

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(profileImageValidator.validate(file))
                .thenReturn(new ValidatedProfileImage("image/png"));
        when(fileStorage.store(file, StorageCategory.MEMBER_PROFILE))
                .thenReturn(storedFile);
        when(memberRepository.saveAndFlush(member))
                .thenThrow(databaseFailure);

        assertThatThrownBy(() ->
                memberService.profileUpload(MEMBER_ID, file)
        ).isSameAs(databaseFailure);

        verify(fileStorage).delete(storedFile.storageKey());
    }

    @Test
    void profileUploadPreservesCleanupFailureAsSuppressedException() {
        Member member = mock(Member.class);
        MockMultipartFile file = profileFile();
        StoredFile storedFile = storedProfile();
        RuntimeException databaseFailure =
                new RuntimeException("DB 저장 실패");
        CustomException cleanupFailure =
                new CustomException(ErrorCode.STORAGE_OPERATION_FAILED);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(profileImageValidator.validate(file))
                .thenReturn(new ValidatedProfileImage("image/png"));
        when(fileStorage.store(file, StorageCategory.MEMBER_PROFILE))
                .thenReturn(storedFile);
        when(memberRepository.saveAndFlush(member))
                .thenThrow(databaseFailure);
        doThrow(cleanupFailure)
                .when(fileStorage)
                .delete(storedFile.storageKey());

        assertThatThrownBy(() ->
                memberService.profileUpload(MEMBER_ID, file)
        ).isSameAs(databaseFailure)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed())
                                .containsExactly(cleanupFailure)
                );
    }

    @Test
    void deleteProfileImageClearsMemberAndQueuesStoredImageDeletion() {
        Member member = mock(Member.class);
        String storageKey = "member-profiles/2026/10/26/profile-key";

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(member.getProfileImageStorageKey()).thenReturn(storageKey);

        memberService.deleteProfileImage(MEMBER_ID);

        verify(member).deleteProfileImage();
        verify(memberRepository).saveAndFlush(member);
        verify(fileStorage, never()).delete(storageKey);

        ArgumentCaptor<StorageDeletionTask> taskCaptor =
                ArgumentCaptor.forClass(StorageDeletionTask.class);
        verify(storageDeletionTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStorageKey())
                .isEqualTo(storageKey);
    }

    @Test
    void deleteProfileImageDoesNotQueueTaskWithoutStoredImage() {
        Member member = mock(Member.class);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        memberService.deleteProfileImage(MEMBER_ID);

        verify(member).deleteProfileImage();
        verify(memberRepository).saveAndFlush(member);
        verifyNoInteractions(storageDeletionTaskRepository, fileStorage);
    }

    private MockMultipartFile profileFile() {
        return new MockMultipartFile(
                "file",
                "profile.png",
                "image/png",
                "image".getBytes()
        );
    }

    private Member activeAuthenticatedMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        return member;
    }

    private Member activePublicMember(Long memberId, String nickname) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        when(member.getNickname()).thenReturn(nickname);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        return member;
    }

    private void mockPublicProfileLookup(
            Member member,
            Member targetMember,
            Friendship friendship
    ) {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(memberRepository.findById(targetMember.getId()))
                .thenReturn(Optional.of(targetMember));
        when(friendshipRepository.findBetweenMembers(
                MEMBER_ID,
                targetMember.getId()
        )).thenReturn(Optional.of(friendship));
    }

    private void assertInactiveMemberProfileImageRejected(
            MemberStatus status
    ) {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(status);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                memberService.getProfileImage(MEMBER_ID)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PROFILE_IMAGE_NOT_FOUND)
        );

        verifyNoInteractions(fileStorage);
    }

    private StoredFile storedProfile() {
        return new StoredFile(
                "member-profiles/2026/10/26/new-key",
                "profile.png",
                "image/png",
                5L
        );
    }
}
