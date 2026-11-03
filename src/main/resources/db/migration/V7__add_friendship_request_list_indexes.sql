CREATE INDEX idx_friendships_requester_status_id_desc
    ON friendships (requester_id, status, id DESC);

CREATE INDEX idx_friendships_receiver_status_id_desc
    ON friendships (receiver_id, status, id DESC);