local stockKey = KEYS[1]
local userKey = KEYS[2]
local pendingKey = KEYS[3]

local userId = ARGV[1]
local orderNo = ARGV[2]
local quantity = tonumber(ARGV[3])

local currentOrderNo = redis.call('HGET', userKey, userId)
if currentOrderNo ~= orderNo then
    redis.call('DEL', pendingKey)
    return {0}
end

if quantity and quantity > 0 then
    redis.call('INCRBY', stockKey, quantity)
end
redis.call('HDEL', userKey, userId)
redis.call('DEL', pendingKey)
return {1}
