-- KEYS[1] = total count key，例如 ip2location:quota:count
-- KEYS[2] = round-robin cursor key，例如 ip2location:round-robin:cursor
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:
-- ARGV[2] = delete count
-- 后续参数为要删除的 quota key 集合

local totalKey = KEYS[1]
local cursorKey = KEYS[2]
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
            and string.sub(key, 1, string.len(prefix)) == prefix
            and seen[key] == nil then
        seen[key] = true
        deleted = deleted + redis.call('DEL', key)
    end
end

local scanCursor = "0"
local total = 0
local activeKeys = 0

repeat
    local result = redis.call('SCAN', scanCursor, 'MATCH', prefix .. '*', 'COUNT', 100)
    scanCursor = result[1]
    local keys = result[2]

    for _, key in ipairs(keys) do
        if key ~= totalKey then
            local keyType = redis.call('TYPE', key)['ok']
            if keyType == 'string' then
                local rawQuota = redis.call('GET', key)
                local quota = tonumber(rawQuota)
                if quota ~= nil then
                    activeKeys = activeKeys + 1
                    total = total + quota
                end
            end
        end
    end
until scanCursor == "0"

redis.call('SET', totalKey, total)
if activeKeys == 0 then
    redis.call('SET', cursorKey, 0)
end

return {0, tostring(deleted), tostring(total)}
