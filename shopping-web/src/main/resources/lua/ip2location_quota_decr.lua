-- KEYS[1] = quota key
-- KEYS[2] = total count key，例如 ip2location:quota:count

local quotaKey = KEYS[1]
local totalKey = KEYS[2]

if redis.call('EXISTS', quotaKey) == 0 then
    return {-1, "quota key not found"}
end

local quota = tonumber(redis.call('GET', quotaKey))
if quota == nil or quota <= 0 then
    return {-1, "quota exhausted"}
end

if redis.call('EXISTS', totalKey) == 0 then
    redis.call('SET', totalKey, 0)
end

redis.call('DECR', quotaKey)
redis.call('DECR', totalKey)

local newQuota = tonumber(redis.call('GET', quotaKey))
local newTotal = tonumber(redis.call('GET', totalKey))

return {0, tostring(newQuota), tostring(newTotal)}
