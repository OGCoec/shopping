-- KEYS[1] = total count key，例如 ip2location:quota:count
-- KEYS[2] = index set key，例如 ip2location:quota:index
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:
-- ARGV[2] = entry count
-- 后续每 4 个参数一组：apiKey, quotaKey, quota, ttlSeconds

local totalKey = KEYS[1]
local indexKey = KEYS[2]
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

-- 从索引获取已有 key，按 apiKey 匹配删除旧 key
local existingKeys = redis.call('SMEMBERS', indexKey)
local oldDeleted = 0

for _, key in ipairs(existingKeys) do
    if key ~= totalKey then
        local keyApiKey = string.match(key, "([^:]+)$")
        if keyApiKey ~= nil and apiKeys[keyApiKey] then
            oldDeleted = oldDeleted + redis.call('DEL', key)
            redis.call('SREM', indexKey, key)
        end
    end
end

-- 写入新 key 并注册到索引
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
    redis.call('SADD', indexKey, quotaKey)
end

-- 用索引重建总额度
local updatedKeys = redis.call('SMEMBERS', indexKey)
local total = 0

if #updatedKeys > 0 then
    local updatedValues = redis.call('MGET', unpack(updatedKeys))
    for i = 1, #updatedKeys do
        local val = updatedValues[i]
        if val == false then
            redis.call('SREM', indexKey, updatedKeys[i])
        else
            local quota = tonumber(val)
            if quota ~= nil then
                total = total + quota
            end
        end
    end
end

redis.call('SET', totalKey, total)

return {0, tostring(entryCount), tostring(oldDeleted), tostring(total)}
