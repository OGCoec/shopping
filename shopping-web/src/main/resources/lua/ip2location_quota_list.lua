-- KEYS[1] = total count key，例如 ip2location:quota:count
-- KEYS[2] = index set key，例如 ip2location:quota:index

local totalKey = KEYS[1]
local indexKey = KEYS[2]

local allKeys = redis.call('SMEMBERS', indexKey)

if #allKeys == 0 then
    redis.call('SET', totalKey, 0)
    return {0, "0", "0"}
end

table.sort(allKeys)

local allValues = redis.call('MGET', unpack(allKeys))

local total = 0
local rows = {}

for i = 1, #allKeys do
    local val = allValues[i]
    if val == false then
        redis.call('SREM', indexKey, allKeys[i])
    else
        local quota = tonumber(val)
        if quota ~= nil then
            total = total + quota
            table.insert(rows, allKeys[i])
            table.insert(rows, tostring(quota))
            table.insert(rows, tostring(redis.call('TTL', allKeys[i])))
        end
    end
end

redis.call('SET', totalKey, total)

local response = {0, tostring(total), tostring(total)}
for _, item in ipairs(rows) do
    table.insert(response, item)
end

return response
