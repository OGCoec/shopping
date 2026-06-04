local processingKey = KEYS[1]
local dirtyKey = KEYS[2]

local cutoffMs = tonumber(ARGV[1])
local nowMs = tonumber(ARGV[2])
local batchSize = tonumber(ARGV[3])
local detailPrefix = ARGV[4]
if not batchSize or batchSize <= 0 then
    return {}
end

local orderNos = redis.call('ZRANGEBYSCORE', processingKey, '-inf', cutoffMs, 'LIMIT', 0, batchSize)
if #orderNos == 0 then
    return {}
end

redis.call('ZREM', processingKey, unpack(orderNos))
for index = 1, #orderNos do
    local score = nowMs
    if detailPrefix and detailPrefix ~= '' then
        local orderJson = redis.call('GET', detailPrefix .. orderNos[index])
        if orderJson then
            local ok, order = pcall(cjson.decode, orderJson)
            if ok and order then
                score = tonumber(order['createdAtEpochMs'] or nowMs) or nowMs
            end
        end
    end
    redis.call('ZADD', dirtyKey, score, orderNos[index])
end

return orderNos
