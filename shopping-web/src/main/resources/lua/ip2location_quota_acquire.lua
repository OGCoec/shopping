-- KEYS[1] = total count key, e.g. ip2location:quota:count
-- KEYS[2] = round-robin cursor key, e.g. ip2location:round-robin:cursor
-- KEYS[3] = index set key, e.g. ip2location:quota:index

local totalKey = KEYS[1]
local cursorKey = KEYS[2]
local indexKey = KEYS[3]

local allKeys = redis.call('SMEMBERS', indexKey)

if #allKeys == 0 then
    redis.call('SET', totalKey, 0)
    redis.call('SET', cursorKey, 0)
    return {-1, "", "0", "quota_key_not_found"}
end

local allValues = redis.call('MGET', unpack(allKeys))

local positiveKeys = {}
local realTotal = 0
local hasAnyValid = false

for i = 1, #allKeys do
    local val = allValues[i]
    if val == false then
        -- key 已过期，从索引中清理
        redis.call('SREM', indexKey, allKeys[i])
    else
        local quota = tonumber(val)
        if quota ~= nil then
            hasAnyValid = true
            if quota > 0 then
                table.insert(positiveKeys, allKeys[i])
                realTotal = realTotal + quota
            end
        end
    end
end

if not hasAnyValid then
    redis.call('SET', totalKey, 0)
    redis.call('SET', cursorKey, 0)
    return {-1, "", "0", "quota_key_not_found"}
end

if realTotal <= 0 or #positiveKeys == 0 then
    redis.call('SET', totalKey, 0)
    redis.call('SET', cursorKey, 0)
    return {-2, "", "0", "quota_count_exhausted"}
end

table.sort(positiveKeys)

local size = #positiveKeys
local rawCursor = tonumber(redis.call('GET', cursorKey) or '0') or 0
local startIndex = rawCursor % size
local selectedKey = positiveKeys[startIndex + 1]
local nextCursor = (startIndex + 1) % size

redis.call('DECR', selectedKey)
local newTotal = realTotal - 1
redis.call('SET', totalKey, newTotal)
redis.call('SET', cursorKey, nextCursor)

return {0, selectedKey, tostring(newTotal)}
