local storedMemberId = redis.call(
    'HGET',
    KEYS[1],
    'memberId'
)
local storedSessionId = redis.call(
    'HGET',
    KEYS[1],
    'sessionId'
)
local storedPurpose = redis.call(
    'HGET',
    KEYS[1],
    'purpose'
)

if not storedMemberId
    or not storedSessionId
    or not storedPurpose then
    return 0
end

if storedMemberId ~= ARGV[1]
    or storedSessionId ~= ARGV[2]
    or storedPurpose ~= ARGV[3] then
    return 0
end

return redis.call('DEL', KEYS[1])
