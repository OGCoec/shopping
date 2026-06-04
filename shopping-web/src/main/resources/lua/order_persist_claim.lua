local dirtyKey = KEYS[1]
local processingKey = KEYS[2]

local nowMs = tonumber(ARGV[1])
local batchSize = tonumber(ARGV[2])
if not batchSize or batchSize <= 0 then
    return {}
end

local orderNos = redis.call('ZRANGE', dirtyKey, 0, batchSize - 1)
if #orderNos == 0 then
    return {}
end

redis.call('ZREM', dirtyKey, unpack(orderNos))
for index = 1, #orderNos do
    redis.call('ZADD', processingKey, nowMs, orderNos[index])
end

return orderNos
