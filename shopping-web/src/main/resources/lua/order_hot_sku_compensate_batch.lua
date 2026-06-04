local itemCount = tonumber(ARGV[1] or '0')
if not itemCount or itemCount <= 0 then
    return {0}
end

local released = 0
for index = 1, itemCount do
    local keyIndex = (index - 1) * 3 + 1
    local argIndex = (index - 1) * 3 + 2

    local stockKey = KEYS[keyIndex]
    local userKey = KEYS[keyIndex + 1]
    local pendingKey = KEYS[keyIndex + 2]
    local userId = ARGV[argIndex]
    local orderNo = ARGV[argIndex + 1]
    local quantity = tonumber(ARGV[argIndex + 2])

    local currentOrderNo = redis.call('HGET', userKey, userId)
    if currentOrderNo == orderNo then
        if quantity and quantity > 0 then
            redis.call('INCRBY', stockKey, quantity)
        end
        redis.call('HDEL', userKey, userId)
        released = released + 1
    end
    redis.call('DEL', pendingKey)
end

return {released}
