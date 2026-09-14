DROP INDEX idx_chat_messages_room_id_id_desc;

CREATE INDEX idx_chat_messages_room_created_at_id_desc
    ON chat_messages (room_id, created_at DESC, id DESC);