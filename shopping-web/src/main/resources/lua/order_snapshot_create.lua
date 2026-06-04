local detailKey = KEYS[1]
local itemKey = KEYS[2]
local userOrderKey = KEYS[3]
local expireKey = KEYS[4]
local dirtyKey = KEYS[5]
local allOrderKey = KEYS[6]

local orderJson = ARGV[1]
local itemJson = ARGV[2]
local orderNo = ARGV[3]
local createdAtMs = tonumber(ARGV[4])
local expireAtMs = tonumber(ARGV[5])
local dirtyAtMs = tonumber(ARGV[6])

redis.call('SET', detailKey, orderJson)
redis.call('SET', itemKey, itemJson)
redis.call('ZADD', userOrderKey, createdAtMs, orderNo)
redis.call('ZADD', allOrderKey, createdAtMs, orderNo)
redis.call('ZADD', expireKey, expireAtMs, orderNo)
redis.call('ZADD', dirtyKey, dirtyAtMs, orderNo)

return {0}
