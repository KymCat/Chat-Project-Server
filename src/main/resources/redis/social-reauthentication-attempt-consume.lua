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
local storedProvider = redis.call(
    'HGET',
    KEYS[1],
    'provider'
)
local storedPurpose = redis.call(
    'HGET',
    KEYS[1],
    'purpose'
)

if storedMemberId ~= ARGV[1]
    or storedSessionId ~= ARGV[2]
    or storedProvider ~= ARGV[3]
    or storedPurpose ~= ARGV[4] then
    return 0
end

return redis.call('DEL', KEYS[1])
