-- KEYS[1] = total count key，例如 ip2location:quota:count
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:

local totalKey = KEYS[1]
local prefix = ARGV[1]

local cursor = "0"
local quotaKeys = {}

repeat
    local result = redis.call('SCAN', cursor, 'MATCH', prefix .. '*', 'COUNT', 100)
    cursor = result[1]
    local keys = result[2]

    for _, key in ipairs(keys) do
        if key ~= totalKey then
            table.insert(quotaKeys, key)
        end
    end
until cursor == "0"

table.sort(quotaKeys)

local total = 0
local rows = {}

for _, key in ipairs(quotaKeys) do
    local keyType = redis.call('TYPE', key)['ok']
    if keyType == 'string' then
        local rawQuota = redis.call('GET', key)
        local quota = tonumber(rawQuota)
        if quota ~= nil then
            total = total + quota
            table.insert(rows, key)
            table.insert(rows, tostring(quota))
            table.insert(rows, tostring(redis.call('TTL', key)))
        end
    end
end

redis.call('SET', totalKey, total)

local response = {0, tostring(total), tostring(total)}
for _, item in ipairs(rows) do
    table.insert(response, item)
end

return response
