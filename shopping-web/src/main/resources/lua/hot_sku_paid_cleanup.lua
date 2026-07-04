local holdKey = KEYS[1]
local orderNo = ARGV[1]
local pendingUserKeyPrefix = ARGV[2]

local skuId = redis.call('HGET', holdKey, 'skuId')
local userId = redis.call('HGET', holdKey, 'userId')
local pendingDeleted = 0

if skuId and skuId ~= '' and userId and userId ~= '' then
    local pendingUserKey = pendingUserKeyPrefix .. skuId .. ':' .. userId
    local currentOrderNo = redis.call('GET', pendingUserKey)
    if currentOrderNo == orderNo then
        pendingDeleted = redis.call('DEL', pendingUserKey)
    end
end

local holdDeleted = redis.call('DEL', holdKey)
return {holdDeleted, pendingDeleted}
