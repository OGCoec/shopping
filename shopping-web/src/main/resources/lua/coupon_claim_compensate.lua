local stockKey = KEYS[1]
local claimedKey = KEYS[2]
local pendingKey = KEYS[3]
local pendingIndexKey = KEYS[4]
local dirtyKey = KEYS[5]

local userId = ARGV[1]
local userCouponId = ARGV[2]
local claimId = ARGV[3]
local couponId = ARGV[4]

local currentUserCouponId = redis.call('HGET', claimedKey, userId)
if currentUserCouponId == userCouponId then
    redis.call('INCR', stockKey)
    redis.call('HDEL', claimedKey, userId)
end
redis.call('DEL', pendingKey)
redis.call('SREM', pendingIndexKey, claimId)
redis.call('SADD', dirtyKey, couponId)
return {1}
