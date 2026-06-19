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

local function user_id_text(value)
    if value == nil then
        return ''
    end
    if type(value) == 'number' then
        return string.format('%.0f', value)
    end
    return tostring(value)
end

local orderJson = redis.call('GET', detailKey)
if not orderJson then
    return {1}
end

local order = cjson.decode(orderJson)
local orderUserId = user_id_text(order['userId'])
if expectedUserId and expectedUserId ~= '' and orderUserId ~= expectedUserId then
    return {5}
end
if orderUserId ~= '' then
    order['userId'] = orderUserId
end
if order['status'] == 'PAID' then
    local currentJson = cjson.encode(order)
    redis.call('SET', detailKey, currentJson)
    redis.call('ZADD', dirtyKey, paidAtMs, orderNo)
    redis.call('ZREM', expireKey, orderNo)
    redis.call('ZREM', closingKey, orderNo)
    return {4, currentJson, '[]'}
end
if order['status'] ~= 'PENDING_PAYMENT' and (not allowClosing or order['status'] ~= 'CLOSING') then
    return {2, order['status'] or ''}
end

order['status'] = 'PAID'
order['paidAt'] = paidAtIso
order['paidAtEpochMs'] = paidAtMs
order['paymentType'] = 'SIMULATED'
order['usedPoints'] = 0
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
