local dirtyKey = KEYS[1]
local processingKey = KEYS[2]
local expireKey = KEYS[3]
local closingKey = KEYS[4]
local allOrderKey = KEYS[5]

local orderCount = tonumber(ARGV[1] or '0')
local index = 2
local orderNos = {}

for i = 1, orderCount do
    orderNos[i] = ARGV[index]
    index = index + 1
end

if orderCount > 0 then
    redis.call('ZREM', processingKey, unpack(orderNos))
end

local terminalCount = tonumber(ARGV[index] or '0')
index = index + 1

for i = 1, terminalCount do
    local orderNo = ARGV[index]
    local detailKey = ARGV[index + 1]
    local itemKey = ARGV[index + 2]
    local userOrderKey = ARGV[index + 3]
    index = index + 4

    redis.call('DEL', detailKey, itemKey)
    redis.call('ZREM', dirtyKey, orderNo)
    redis.call('ZREM', expireKey, orderNo)
    redis.call('ZREM', closingKey, orderNo)
    redis.call('ZREM', allOrderKey, orderNo)
    if userOrderKey and userOrderKey ~= '' then
        redis.call('ZREM', userOrderKey, orderNo)
    end
end

return {orderCount, terminalCount}
