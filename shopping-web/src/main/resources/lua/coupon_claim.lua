local templateKey = KEYS[1]
local stockKey = KEYS[2]
local claimedKey = KEYS[3]
local pendingKey = KEYS[4]
local pendingIndexKey = KEYS[5]
local dirtyKey = KEYS[6]

local nowMs = tonumber(ARGV[1])
local userId = ARGV[2]
local userCouponId = ARGV[3]
local claimId = ARGV[4]
local couponId = ARGV[5]
local createdAtMs = ARGV[6]

local status = redis.call('HGET', templateKey, 'status')
if not status then
    return {1}
end
if status ~= 'ACTIVE' then
    return {2}
end

local receiveStartAt = tonumber(redis.call('HGET', templateKey, 'receiveStartAtEpochMs') or '')
local receiveEndAt = tonumber(redis.call('HGET', templateKey, 'receiveEndAtEpochMs') or '')
local validStartAt = redis.call('HGET', templateKey, 'validStartAtEpochMs')
local validEndAt = redis.call('HGET', templateKey, 'validEndAtEpochMs')
if not receiveStartAt or not receiveEndAt or not validStartAt or not validEndAt then
    return {1}
end
if nowMs < receiveStartAt then
    return {3}
end
if nowMs > receiveEndAt then
    return {4}
end

if redis.call('HEXISTS', claimedKey, userId) == 1 then
    return {5, redis.call('HGET', claimedKey, userId)}
end

local stock = tonumber(redis.call('GET', stockKey) or '')
if not stock then
    return {1}
end
if stock <= 0 then
    return {6}
end

local remaining = redis.call('DECR', stockKey)
if tonumber(remaining) < 0 then
    redis.call('INCR', stockKey)
    return {6}
end

redis.call('HSET', claimedKey, userId, userCouponId)
redis.call('HSET', pendingKey,
    'claimId', claimId,
    'couponId', couponId,
    'userCouponId', userCouponId,
    'userId', userId,
    'validStartAtEpochMs', validStartAt,
    'validEndAtEpochMs', validEndAt,
    'createdAtEpochMilli', createdAtMs,
    'retryCount', '0')
redis.call('SADD', pendingIndexKey, claimId)
redis.call('SADD', dirtyKey, couponId)
return {0, validStartAt, validEndAt, remaining}
