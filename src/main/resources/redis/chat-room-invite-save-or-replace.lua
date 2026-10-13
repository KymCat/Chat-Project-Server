-- KEYS[1]: roomId → token Key
-- KEYS[2]: 기존 token → 초대 링크 Hash Key
-- KEYS[3]: 새로운 token → 초대 링크 Hash Key

-- ARGV[1]: Java에서 미리 조회한 기존 Token, 없으면 빈 문자열
-- ARGV[2]: 새로운 Token
-- ARGV[3]: roomId
-- ARGV[4]: expiresAt
-- ARGV[5]: TTL milliseconds

-- 반환값
-- 1: 토큰 저장 성공
-- 0: 실제 저장된 토큰과 불일치, 실패

local room_id_key = KEYS[1]
local old_token_key = KEYS[2]
local new_token_key = KEYS[3]

local expected_old_token = ARGV[1]
local new_token = ARGV[2]
local room_id = ARGV[3]
local expires_at = ARGV[4]
local ttl_millis = tonumber(ARGV[5])

-- 쓰기 작업 전에 인수를 먼저 검증한다.
if ttl_millis == nil or ttl_millis <= 0 then
    return redis.error_reply("INVALID_TTL")
end

-- Java에서 조회한 이후 다른 요청이 값을 바꿨는지 확인한다.
local current_token = redis.call("GET", room_id_key)

if current_token == false then
    current_token = ""
end

if current_token ~= expected_old_token then
    return 0
end

-- 기존 Token Hash를 제거한다.
if expected_old_token ~= "" then
    redis.call("DEL", old_token_key)
end

-- roomId → 새로운 Token
redis.call(
        "SET",
        room_id_key,
        new_token,
        "PX",
        ARGV[5]
)

-- 새로운 Token → 초대 링크 정보
redis.call(
        "HSET",
        new_token_key,
        "roomId",
        room_id,
        "expiresAt",
        expires_at
)

redis.call(
        "PEXPIRE",
        new_token_key,
        ARGV[5]
)

return 1