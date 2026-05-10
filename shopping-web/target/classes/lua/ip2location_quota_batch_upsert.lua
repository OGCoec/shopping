-- KEYS[1] = total count key，例如 ip2location:quota:count
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:
-- ARGV[2] = entry count
-- 后续每 4 个参数一组：apiKey, quotaKey, quota, ttlSeconds

local totalKey = KEYS[1]
local prefix = ARGV[1]
local entryCount = tonumber(ARGV[2])

if entryCount == nil or entryCount <= 0 then
    return {-2, "empty entries"}
end

local apiKeys = {}
local entries = {}
local argIndex = 3

for i = 1, entryCount do
    local apiKey = ARGV[argIndex]
    local quotaKey = ARGV[argIndex + 1]
    local quota = tonumber(ARGV[argIndex + 2])
    local ttlSeconds = tonumber(ARGV[argIndex + 3])
    argIndex = argIndex + 4

    if apiKey == nil or apiKey == "" then
        return {-3, "invalid api key"}
    end
    if quotaKey == nil or string.sub(quotaKey, 1, string.len(prefix)) ~= prefix then
        return {-4, "invalid quota key"}
    end
    if quota == nil or quota < 0 then
        return {-5, "invalid quota"}
    end
    if ttlSeconds == nil then
        ttlSeconds = -1
    end

    apiKeys[apiKey] = true
    table.insert(entries, {quotaKey, quota, ttlSeconds})
end

local cursor = "0"
local oldDeleted = 0

repeat
    local result = redis.call('SCAN', cursor, 'MATCH', prefix .. '*', 'COUNT', 100)
    cursor = result[1]
    local keys = result[2]

    for _, key in ipairs(keys) do
        if key ~= totalKey then
            local apiKey = string.match(key, "([^:]+)$")
            if apiKey ~= nil and apiKeys[apiKey] then
                oldDeleted = oldDeleted + redis.call('DEL', key)
            end
        end
    end
until cursor == "0"

for _, entry in ipairs(entries) do
    local quotaKey = entry[1]
    local quota = entry[2]
    local ttlSeconds = entry[3]

    redis.call('SET', quotaKey, quota)
    if ttlSeconds > 0 then
        redis.call('EXPIRE', quotaKey, ttlSeconds)
    else
        redis.call('PERSIST', quotaKey)
    end
end

cursor = "0"
local total = 0

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
                    total = total + quota
                end
            end
        end
    end
until cursor == "0"

redis.call('SET', totalKey, total)

return {0, tostring(entryCount), tostring(oldDeleted), tostring(total)}
