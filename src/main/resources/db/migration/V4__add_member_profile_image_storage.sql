ALTER TABLE members
      ADD COLUMN profile_image_storage_key VARCHAR(1024) NULL,
      ADD COLUMN profile_image_content_type VARCHAR(100) NULL,
      ADD COLUMN profile_image_size_bytes BIGINT NULL;

CREATE UNIQUE INDEX uq_members_profile_image_storage_key
    ON members(profile_image_storage_key)
    WHERE profile_image_storage_key IS NOT NULL;

ALTER TABLE members
    ADD CONSTRAINT ck_members_profile_image_size_bytes
        CHECK (
            profile_image_size_bytes IS NULL
            OR profile_image_size_bytes > 0
        ),

    -- storageKey가 있다면 다운로드 필요한 메타데이터도 모두 있어야 한다.
    ADD CONSTRAINT ck_members_profile_image_storage_metadata
        CHECK(
            (
                profile_image_storage_key IS NULL
                AND profile_image_content_type IS NULL
                AND profile_image_size_bytes IS NULL
            )
            OR
            (
                profile_image_storage_key IS NOT NULL
                AND profile_image_content_type IS NOT NULL
                AND profile_image_size_bytes IS NOT NULL
            )
        ),

    -- 외부 소셜 계정에서 사용하는 사진과 동시에 사용하지 않는다.
    ADD CONSTRAINT ck_members_profile_image_source
        CHECK(
            profile_image_url IS NULL
            OR profile_image_storage_key IS NULL
        );
