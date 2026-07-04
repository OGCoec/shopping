local pendingUserKey = KEYS[1]
local orderNo = ARGV[1]

local currentOrderNo = redis.call('GET', pendingUserKey)
if currentOrderNo == orderNo then
    return redis.call('DEL', pendingUserKey)
end

return 0
