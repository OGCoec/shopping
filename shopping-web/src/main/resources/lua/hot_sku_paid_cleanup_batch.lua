local itemCount = tonumber(ARGV[1] or '0')
local pendingUserKeyPrefix = ARGV[2]
if not itemCount or itemCount <= 0 then
    return {0, 0}
end

local holdDeleted = 0
local pendingDeleted = 0

for index = 1, itemCount do
    local holdKey = KEYS[index]
    local orderNo = ARGV[index + 2]
    local skuId = redis.call('HGET', holdKey, 'skuId')
    local userId = redis.call('HGET', holdKey, 'userId')

    if skuId and skuId ~= '' and userId and userId ~= '' then
        local pendingUserKey = pendingUserKeyPrefix .. skuId .. ':' .. userId
        local currentOrderNo = redis.call('GET', pendingUserKey)
        if currentOrderNo == orderNo then
            pendingDeleted = pendingDeleted + redis.call('DEL', pendingUserKey)
        end
    end

    holdDeleted = holdDeleted + redis.call('DEL', holdKey)
end

return {holdDeleted, pendingDeleted}
