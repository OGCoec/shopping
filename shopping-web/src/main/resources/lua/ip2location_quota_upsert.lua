-- KEYS[1] = quota key
-- KEYS[2] = total count key，例如 ip2location:quota:count
-- ARGV[1] = new quota value，例如 50000

local quotaKey = KEYS[1]
local totalKey = KEYS[2]
local newQuota = tonumber(ARGV[1])

if newQuota == nil then
    return {-2, "invalid quota"}
end

local oldQuota = redis.call('GET', quotaKey)

if redis.call('EXISTS', totalKey) == 0 then
    redis.call('SET', totalKey, 0)
end

if oldQuota == false then
    redis.call('SET', quotaKey, newQuota)
    redis.call('INCRBY', totalKey, newQuota)
    return {1, tostring(newQuota), tostring(redis.call('GET', totalKey))}
else
    oldQuota = tonumber(oldQuota)
    local diff = newQuota - oldQuota
    redis.call('SET', quotaKey, newQuota)
    redis.call('INCRBY', totalKey, diff)
    return {2, tostring(newQuota), tostring(redis.call('GET', totalKey))}
end
