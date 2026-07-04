local dirtyKey = KEYS[1]
local itemCount = tonumber(ARGV[1] or '0')
if not itemCount or itemCount <= 0 then
    return {0}
end

local released = 0
for index = 1, itemCount do
    local keyIndex = (index - 1) * 3 + 2
    local argIndex = (index - 1) * 4 + 2

    local stockKey = KEYS[keyIndex]
    local pendingUserKey = KEYS[keyIndex + 1]
    local holdKey = KEYS[keyIndex + 2]
    local userId = ARGV[argIndex]
    local orderNo = ARGV[argIndex + 1]
    local quantity = tonumber(ARGV[argIndex + 2])
    local skuId = ARGV[argIndex + 3]

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
        released = released + 1
    end

    local currentOrderNo = redis.call('GET', pendingUserKey)
    if currentOrderNo == orderNo then
        redis.call('DEL', pendingUserKey)
    end
end

return {released}
