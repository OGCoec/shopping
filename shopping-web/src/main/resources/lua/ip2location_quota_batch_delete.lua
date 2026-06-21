-- KEYS[1] = total count key，例如 ip2location:quota:count
-- KEYS[2] = round-robin cursor key，例如 ip2location:round-robin:cursor
-- KEYS[3] = index set key，例如 ip2location:quota:index
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:
-- ARGV[2] = delete count
-- 后续参数为要删除的 quota key 集合

local totalKey = KEYS[1]
local cursorKey = KEYS[2]
local indexKey = KEYS[3]
local prefix = ARGV[1]
local deleteCount = tonumber(ARGV[2])

if deleteCount == nil or deleteCount <= 0 then
    return {-2, "empty keys"}
end

local deleted = 0
local seen = {}
local argIndex = 3

for i = 1, deleteCount do
    local key = ARGV[argIndex]
    argIndex = argIndex + 1

    if key ~= nil
            and key ~= ""
            and key ~= totalKey
            and key ~= cursorKey
            and key ~= indexKey
            and string.sub(key, 1, string.len(prefix)) == prefix
            and seen[key] == nil then
        seen[key] = true
        deleted = deleted + redis.call('DEL', key)
        redis.call('SREM', indexKey, key)
    end
end

-- 用索引重建总额度
local remainingKeys = redis.call('SMEMBERS', indexKey)
local total = 0
local activeKeys = 0

if #remainingKeys > 0 then
    local remainingValues = redis.call('MGET', unpack(remainingKeys))
    for i = 1, #remainingKeys do
        local val = remainingValues[i]
        if val == false then
            redis.call('SREM', indexKey, remainingKeys[i])
        else
            local quota = tonumber(val)
            if quota ~= nil then
                activeKeys = activeKeys + 1
                total = total + quota
            end
        end
    end
end

redis.call('SET', totalKey, total)
if activeKeys == 0 then
    redis.call('SET', cursorKey, 0)
end

return {0, tostring(deleted), tostring(total)}
