local lockKey = KEYS[1]
local expectedValue = ARGV[1]

local currentValue = redis.call('GET', lockKey)
if currentValue == expectedValue then
    return redis.call('DEL', lockKey)
end

return 0
