local metaKey = KEYS[1]
local stockKey = KEYS[2]
local userKey = KEYS[3]
local pendingKey = KEYS[4]

local nowMs = tonumber(ARGV[1])
local userId = ARGV[2]
local orderNo = ARGV[3]
local skuId = ARGV[4]
local quantity = tonumber(ARGV[5])

local status = redis.call('HGET', metaKey, 'status')
if not status then
    return {1}
end
if status ~= 'ENABLED' then
    return {2}
end

local startAtText = redis.call('HGET', metaKey, 'startAtEpochMs')
local endAtText = redis.call('HGET', metaKey, 'endAtEpochMs')
if startAtText and startAtText ~= '' then
    local startAt = tonumber(startAtText)
    if startAt and nowMs < startAt then
        return {3}
    end
end
if endAtText and endAtText ~= '' then
    local endAt = tonumber(endAtText)
    if endAt and nowMs >= endAt then
        return {4}
    end
end

if redis.call('HEXISTS', userKey, userId) == 1 then
    local existingOrderNo = redis.call('HGET', userKey, userId)
    if existingOrderNo == orderNo then
        return {0, tonumber(redis.call('GET', stockKey) or '0')}
    end
    return {5, existingOrderNo}
end

local stock = tonumber(redis.call('GET', stockKey) or '')
if not stock then
    return {1}
end
if not quantity or quantity <= 0 then
    return {6}
end
if stock < quantity then
    return {7}
end

local remaining = redis.call('DECRBY', stockKey, quantity)
if tonumber(remaining) < 0 then
    redis.call('INCRBY', stockKey, quantity)
    return {7}
end

redis.call('HSET', userKey, userId, orderNo)
redis.call('HSET', pendingKey,
    'orderNo', orderNo,
    'skuId', skuId,
    'userId', userId,
    'quantity', tostring(quantity),
    'createdAtEpochMs', tostring(nowMs))
redis.call('PEXPIRE', pendingKey, 1800000)

return {0, remaining}
