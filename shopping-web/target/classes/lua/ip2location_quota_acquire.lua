-- KEYS[1] = total count key, e.g. ip2location:quota:count
-- KEYS[2] = round-robin cursor key, e.g. ip2location:round-robin:cursor
-- ARGV[1] = quota key prefix, e.g. ip2location:quota:

local totalKey = KEYS[1]
local cursorKey = KEYS[2]
local prefix = ARGV[1]

local cursor = "0"
local quotaKeys = {}
local positiveKeys = {}
local realTotal = 0

repeat
    local result = redis.call('SCAN', cursor, 'MATCH', prefix .. '*', 'COUNT', 100)
    cursor = result[1]
    local keys = result[2]

    for _, key in ipairs(keys) do
        if key ~= totalKey then
            local keyType = redis.call('TYPE', key)['ok']
            if keyType == 'string' then
                local rawQuota = redis.call('GET', key)
                local quota = tonumber(rawQuota)
                if quota ~= nil then
                    table.insert(quotaKeys, key)
                    if quota > 0 then
                        table.insert(positiveKeys, key)
                        realTotal = realTotal + quota
                    end
                end
            end
        end
    end
until cursor == "0"

if #quotaKeys == 0 then
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
