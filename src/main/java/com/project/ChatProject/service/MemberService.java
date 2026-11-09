package com.project.ChatProject.service;

import com.project.ChatProject.dto.enums.ProfileRelationStatus;
import com.project.ChatProject.dto.request.SignupRequest;
import com.project.ChatProject.dto.request.SocialProfileProviderRequest;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.MemberPublicProfileResponse;
import com.project.ChatProject.dto.response.SignupResponse;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.entity.*;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.profile.ProfileImageValidator;
import com.project.ChatProject.profile.ValidatedProfileImage;
import com.project.ChatProject.profile.google.DownloadedProfileImage;
import com.project.ChatProject.profile.google.GoogleProfileImageClient;
import com.project.ChatProject.repository.*;
import com.project.ChatProject.storage.FileStorage;
import com.project.ChatProject.storage.StorageCategory;
import com.project.ChatProject.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final MemberCredentialRepository memberCredentialRepository;
    private final FileStorage fileStorage;
    private final ProfileImageValidator profileImageValidator;
    private final StorageDeletionTaskRepository storageDeletionTaskRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final FriendshipRepository friendshipRepository;
    private final GoogleProfileImageClient googleProfileImageClient;

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = validateMember(memberId);

        return MemberProfileResponse.from(member);
    }

    @Transactional(readOnly = true)
    public MemberPublicProfileResponse getPublicProfile(
            Long memberId,
            Long targetMemberId
    ) {
        Member member = validateMember(memberId);
        if (memberId.equals(targetMemberId)) {
            return MemberPublicProfileResponse.from(
                    member,
                    ProfileRelationStatus.SELF
            );
        }

        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        ProfileRelationStatus relationStatus =
                findRelation(memberId, targetMemberId);

        return MemberPublicProfileResponse.from(
                targetMember,
                relationStatus
        );
    }

    @Transactional(readOnly = true)
    public ProfileImageResult getProfileImage(
            Long memberId
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_NOT_FOUND
            );
        }

        if (!member.hasStoredProfileImage()) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_NOT_FOUND
            );
        }

        Resource resource = fileStorage.load(
                member.getProfileImageStorageKey()
        );

        return ProfileImageResult.stored(
                resource,
                member.getProfileImageContentType(),
                member.getProfileImageSizeBytes()
        );
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        if (memberRepository.existsByEmail(email))
            throw new CustomException(ErrorCode.DUPLICATE_MEMBER_EMAIL);

        String passwordHash = passwordEncoder.encode(request.password());

        Member member = Member.create(
                email,
                request.nickname());

        Member memberSaved = memberRepository.saveAndFlush(member);
        memberCredentialRepository.save(
                MemberCredential.create(member, passwordHash)
        );

        return SignupResponse.from(memberSaved);
    }

    @Transactional
    public void editNickname(
            Long memberId,
            String newNickname
    ) {
        Member member = validateMember(memberId);
        member.updateNickname(newNickname);
    }

    @Transactional
    public void applySocialProfileImage(
            Long memberId,
            SocialProfileProviderRequest request
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        SocialProvider provider = request.provider();
        SocialAccount account = socialAccountRepository
                        .findByProviderAndMemberId(
                                provider,
                                memberId
                        )
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND
                                )
                        );
        String socialProfileImageUrl =
                account.getProviderProfileImageUrl();

        if (!StringUtils.hasText(
                socialProfileImageUrl
        )) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_NOT_FOUND
            );
        }

        downloadAndStoreSocialProfileImage(
                member,
                socialProfileImageUrl
        );
    }

    @Transactional
    public void profileUpload(
            Long memberId,
            MultipartFile file
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        ValidatedProfileImage validated =
                profileImageValidator.validate(file);

        StoredFile storedFile =
                fileStorage.store(
                        file,
                        StorageCategory.MEMBER_PROFILE
                );

        replaceStoredProfileImage(
                member,
                storedFile,
                validated.contentType()
        );
    }

    @Transactional
    public void deleteProfileImage(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        String previousStorageKey =
                member.getProfileImageStorageKey();

        member.deleteProfileImage();

        // 이전 스토리지키를 S3 객체 삭제를 위한 Task 테이블에 저장
        if (previousStorageKey != null) {
            StorageDeletionTask deletionTask =
                    StorageDeletionTask.create(previousStorageKey);

            storageDeletionTaskRepository.save(deletionTask);
        }

        memberRepository.saveAndFlush(member);
    }

    @Transactional
    public void updateStatusMessage(
            Long memberId,
            String newStatusMessage
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        member.updateStatusMessage(newStatusMessage);
    }

    @Transactional
    public void initializeSocialProfileImageIfAbsent(
            Long memberId,
            String socialProfileImageUrl
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        /*
         * 사용자가 업로드했거나 이전 로그인에서 이미 S3에 저장된
         * 프로필 이미지는 Google 로그인으로 덮어쓰지 않게 return
         */
        if (member.hasStoredProfileImage()) {
            return;
        }

        /*
         * Google 계정에 프로필 이미지가 없는 것은
         * 로그인 실패 사유가 아니도록 멱등성 코딩
         */
        if (!StringUtils.hasText(
                socialProfileImageUrl
        )) {
            return;
        }

        downloadAndStoreSocialProfileImage(
                member,
                socialProfileImageUrl
        );
    }

    // === private method ===

    private void downloadAndStoreSocialProfileImage(
            Member member,
            String socialProfileImageUrl
    ) {
        DownloadedProfileImage downloaded =
                googleProfileImageClient.download(
                        socialProfileImageUrl
                );

        ValidatedProfileImage validated =
                profileImageValidator.validate(
                        downloaded.content()
                );

        StoredFile storedFile =
                fileStorage.store(
                        downloaded.content(),
                        "google-profile-image",
                        validated.contentType(),
                        StorageCategory.MEMBER_PROFILE
                );

        replaceStoredProfileImage(
                member,
                storedFile,
                validated.contentType()
        );
    }

    private void replaceStoredProfileImage(
            Member member,
            StoredFile storedFile,
            String validatedContentType
    ) {
        String previousStorageKey =
                member.getProfileImageStorageKey();

        try {
            member.updateProfileImage(
                    storedFile.storageKey(),
                    validatedContentType,
                    storedFile.sizeBytes()
            );

            if (previousStorageKey != null) {
                StorageDeletionTask deletionTask =
                        StorageDeletionTask.create(
                                previousStorageKey
                        );

                storageDeletionTaskRepository.save(
                        deletionTask
                );
            }

            /*
             * Member는 영속 상태지만 DB 반영 실패를 이 method 안에서
             * 감지해 새 S3 object를 보상 삭제하기 위해 flush
             */
            memberRepository.saveAndFlush(member);
        } catch (RuntimeException exception) {
            try {
                fileStorage.delete(
                        storedFile.storageKey()
                );
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(
                        cleanupException
                );
            }

            throw exception;
        }
    }

    private Member validateMember(Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        if (member.getStatus().equals(MemberStatus.SUSPENDED)) {
            throw new CustomException(
                    ErrorCode.MEMBER_BLOCKED
            );
        }

        if (member.getStatus().equals(MemberStatus.WITHDRAWN)) {
            throw new CustomException(
                    ErrorCode.MEMBER_WITHDRAWN
            );
        }

        return member;
    }

    private ProfileRelationStatus findRelation(
            Long memberId,
            Long targetMemberId
    ) {
        return friendshipRepository
                .findBetweenMembers(memberId, targetMemberId)
                .map(friendship ->
                        resolveRelationStatus(friendship, memberId)
                )
                .orElse(ProfileRelationStatus.NONE);
    }

    private ProfileRelationStatus resolveRelationStatus(
            Friendship friendship,
            Long memberId
    ) {
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return ProfileRelationStatus.ACCEPTED;
        }

        if (friendship.getRequester().getId().equals(memberId)) {
            return ProfileRelationStatus.PENDING_SENT;
        }

        return ProfileRelationStatus.PENDING_RECEIVED;
    }
}
