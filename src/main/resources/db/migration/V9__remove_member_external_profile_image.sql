-- 기존 Member URL이 Google SocialAccount에 없으면 보존
UPDATE social_accounts sa
SET provider_profile_image_url = m.profile_image_url
FROM members m
WHERE sa.member_id = m.id
  AND sa.provider = 'GOOGLE'
  AND sa.provider_profile_image_url IS NULL
  AND m.profile_image_url IS NOT NULL;

-- profile_image_url을 참조하는 기존 제약조건을 먼저 제거
ALTER TABLE members
    DROP CONSTRAINT ck_members_profile_image_source;

-- Member에서는 자체 저장 이미지 metadata만 관리
ALTER TABLE members
    DROP COLUMN profile_image_url;