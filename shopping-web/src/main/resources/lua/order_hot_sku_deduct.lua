local metaKey = KEYS[1]
local stockKey = KEYS[2]
local pendingUserKey = KEYS[3]
local holdKey = KEYS[4]
local dirtyKey = KEYS[5]

local nowMs = tonumber(ARGV[1])
local userId = ARGV[2]
local orderNo = ARGV[3]
local skuId = ARGV[4]
local quantity = tonumber(ARGV[5])
local pendingUserTtlMs = tonumber(ARGV[6])
local holdTtlMs = tonumber(ARGV[7])

local function delete_pending_if_current()
    local currentOrderNo = redis.call('GET', pendingUserKey)
    if currentOrderNo == orderNo then
        redis.call('DEL', pendingUserKey)
    end
end

local status = redis.call('HGET', metaKey, 'status')
if not status then
    delete_pending_if_current()
    return {1}
end
if status ~= 'ENABLED' then
    delete_pending_if_current()
    return {2}
end

local startAtText = redis.call('HGET', metaKey, 'startAtEpochMs')
local endAtText = redis.call('HGET', metaKey, 'endAtEpochMs')
if startAtText and startAtText ~= '' then
    local startAt = tonumber(startAtText)
    if startAt and nowMs < startAt then
        delete_pending_if_current()
        return {3}
    end
end
if endAtText and endAtText ~= '' then
    local endAt = tonumber(endAtText)
    if endAt and nowMs >= endAt then
        delete_pending_if_current()
        return {4}
    end
end

local pendingOrderNo = redis.call('GET', pendingUserKey)
if pendingOrderNo and pendingOrderNo ~= orderNo then
    return {5, pendingOrderNo}
end

if redis.call('EXISTS', holdKey) == 1 then
    if pendingUserTtlMs and pendingUserTtlMs > 0 then
        redis.call('SET', pendingUserKey, orderNo, 'PX', pendingUserTtlMs)
    end
    if holdTtlMs and holdTtlMs > 0 then
        redis.call('PEXPIRE', holdKey, holdTtlMs)
    end
    return {0, tonumber(redis.call('GET', stockKey) or '0')}
end

local stock = tonumber(redis.call('GET', stockKey) or '')
if not stock then
    delete_pending_if_current()
    return {1}
end
if not quantity or quantity <= 0 then
    delete_pending_if_current()
    return {6}
end
if stock < quantity then
    delete_pending_if_current()
    return {7}
end

local remaining = redis.call('DECRBY', stockKey, quantity)
if tonumber(remaining) < 0 then
    redis.call('INCRBY', stockKey, quantity)
    delete_pending_if_current()
    return {7}
end

redis.call('HSET', holdKey,
    'skuId', skuId,
    'userId', userId,
    'quantity', tostring(quantity),
    'createdAtEpochMs', tostring(nowMs))
if holdTtlMs and holdTtlMs > 0 then
    redis.call('PEXPIRE', holdKey, holdTtlMs)
end
if pendingUserTtlMs and pendingUserTtlMs > 0 then
    redis.call('SET', pendingUserKey, orderNo, 'PX', pendingUserTtlMs)
end
redis.call('SADD', dirtyKey, skuId)

return {0, remaining}
