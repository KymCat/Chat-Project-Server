package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.SignupRequest;
import com.project.ChatProject.dto.request.SocialProfileProviderRequest;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.SignupResponse;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MemberCredential;
import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.StorageDeletionTask;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.profile.ProfileImageValidator;
import com.project.ChatProject.profile.ValidatedProfileImage;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.repository.SocialAccountRepository;
import com.project.ChatProject.repository.StorageDeletionTaskRepository;
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

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        return MemberProfileResponse.from(member);
    }

    @Transactional(readOnly = true)
    public ProfileImageResult getProfileImage(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        if (member.hasStoredProfileImage()) {
            Resource resource = fileStorage.load(
                    member.getProfileImageStorageKey()
            );

            return ProfileImageResult.stored(
                    resource,
                    member.getProfileImageContentType(),
                    member.getProfileImageSizeBytes()
            );
        }

        if (member.getProfileImageUrl() != null) {
            return ProfileImageResult.external(
                    member.getProfileImageUrl()
            );
        }

        throw new CustomException(
                ErrorCode.PROFILE_IMAGE_NOT_FOUND
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

        if (!StringUtils.hasText(socialProfileImageUrl)) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_NOT_FOUND
            );
        }

        String previousStorageKey =
                member.getProfileImageStorageKey();

        member.applyExternalProfileImage(
                socialProfileImageUrl
        );

        if (previousStorageKey != null) {
            StorageDeletionTask deletionTask =
                    StorageDeletionTask.create(previousStorageKey);

            storageDeletionTaskRepository.save(deletionTask);
        }

        memberRepository.saveAndFlush(member);
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

        String previousStorageKey =
                member.getProfileImageStorageKey();

        ValidatedProfileImage validated = profileImageValidator.validate(file);
        StoredFile storedFile = fileStorage.store(
                file,
                StorageCategory.MEMBER_PROFILE
        );

        try {
            member.updateProfileImage(
                    storedFile.storageKey(),
                    validated.contentType(),
                    storedFile.sizeBytes()
            );

            if (previousStorageKey != null) {
                StorageDeletionTask deletionTask =
                        StorageDeletionTask.create(previousStorageKey);

                storageDeletionTaskRepository.save(deletionTask);
            }

            // member가 이미 영속 상태이지만 보상 삭제를 위한 saveAndFlush
            memberRepository.saveAndFlush(member);
        } catch (RuntimeException exception) {
            try {
                // DB 반영 실패 시, S3 저장된 파일을 보상 삭제
                fileStorage.delete(storedFile.storageKey());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
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
}
