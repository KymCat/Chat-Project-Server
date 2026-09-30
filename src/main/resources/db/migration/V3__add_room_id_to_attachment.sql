ALTER TABLE attachments
    ADD COLUMN room_id BIGINT NULL;

-- 현재 메세지 연결된 첨부파일 메세지의 room_id로 복원
UPDATE attachments a
SET room_id = cm.room_id
FROM chat_messages cm
WHERE cm.attachment_id = a.id;

-- room_id를 복원할 수 없는 기존 데이터가 있으면
-- 임의로 배정하지 않고 migration을 중
DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM attachments
        WHERE room_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot migrate attachments: attachment without room_id exists';
END IF;
END
$$;

ALTER TABLE attachments
    ALTER COLUMN room_id SET NOT NULL;

ALTER TABLE attachments
    ADD CONSTRAINT fk_attachments_room_id
        FOREIGN KEY (room_id)
            REFERENCES chat_rooms (id)
            ON DELETE RESTRICT;

CREATE INDEX idx_attachments_room_id
    ON attachments (room_id);