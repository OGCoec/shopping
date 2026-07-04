local stockKey = KEYS[1]
local pendingUserKey = KEYS[2]
local holdKey = KEYS[3]
local dirtyKey = KEYS[4]

local userId = ARGV[1]
local orderNo = ARGV[2]
local quantity = tonumber(ARGV[3])
local skuId = ARGV[4]

local holdExists = redis.call('EXISTS', holdKey)
if holdExists == 1 then
    local holdQuantity = tonumber(redis.call('HGET', holdKey, 'quantity') or '')
    local holdSkuId = redis.call('HGET', holdKey, 'skuId')
    if holdQuantity and holdQuantity > 0 then
        redis.call('INCRBY', stockKey, holdQuantity)
        redis.call('SADD', dirtyKey, holdSkuId or skuId)
    elseif quantity and quantity > 0 then
        redis.call('INCRBY', stockKey, quantity)
        redis.call('SADD', dirtyKey, skuId)
    end
    redis.call('DEL', holdKey)
end

local currentOrderNo = redis.call('GET', pendingUserKey)
if currentOrderNo == orderNo then
    redis.call('DEL', pendingUserKey)
end

return {holdExists}
