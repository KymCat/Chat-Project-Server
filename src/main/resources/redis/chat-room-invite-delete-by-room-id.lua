-- KEYS[1]: roomIdTokenKey
-- KEYS[2]: tokenKey

-- ARGV[1]: expectedToken

-- 반환값
-- 1: 삭제 성공 및 성공 처리
-- 0: 실패

local room_id_key = KEYS[1]
local token_key = KEYS[2]

local expected_token = ARGV[1]

local current_token = redis.call("GET", room_id_key)

-- 이미 삭제되었거나 만료됐다면 멱등적으로 성공 처리
if current_token == false then
    return 1
end

if current_token ~= expected_token then
    return 0
end

-- 삭제 시작
redis.call(
        "DEL",
        room_id_key,
        token_key
)

return 1