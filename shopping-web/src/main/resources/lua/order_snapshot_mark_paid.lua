local detailKey = KEYS[1]
local dirtyKey = KEYS[2]
local expireKey = KEYS[3]
local closingKey = KEYS[4]

local paidAtIso = ARGV[1]
local paidAtMs = tonumber(ARGV[2])
local orderNo = ARGV[3]
local externalTradeNo = ARGV[4]
local expectedUserId = ARGV[5]
local allowClosing = ARGV[6] ~= '0'

local orderJson = redis.call('GET', detailKey)
if not orderJson then
    return {1}
end

local order = cjson.decode(orderJson)
if expectedUserId and expectedUserId ~= '' and tostring(order['userId'] or '') ~= expectedUserId then
    return {5}
end
if order['status'] == 'PAID' then
    redis.call('ZADD', dirtyKey, paidAtMs, orderNo)
    redis.call('ZREM', expireKey, orderNo)
    redis.call('ZREM', closingKey, orderNo)
    return {4, orderJson, '[]'}
end
if order['status'] ~= 'PENDING_PAYMENT' and (not allowClosing or order['status'] ~= 'CLOSING') then
    return {2, order['status'] or ''}
end

order['status'] = 'PAID'
order['paidAt'] = paidAtIso
order['paidAtEpochMs'] = paidAtMs
if externalTradeNo and externalTradeNo ~= '' then
    order['externalTradeNo'] = externalTradeNo
end
order['updatedAt'] = paidAtIso
order['updatedAtEpochMs'] = paidAtMs
order['version'] = tonumber(order['version'] or 1) + 1

local updatedJson = cjson.encode(order)
redis.call('SET', detailKey, updatedJson)
redis.call('ZADD', dirtyKey, paidAtMs, orderNo)
redis.call('ZREM', expireKey, orderNo)
redis.call('ZREM', closingKey, orderNo)

return {0, updatedJson, '[]'}
