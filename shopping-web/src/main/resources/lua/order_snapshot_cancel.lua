local detailKey = KEYS[1]
local itemKey = KEYS[2]
local dirtyKey = KEYS[3]

local userId = ARGV[1]
local nowIso = ARGV[2]
local nowMs = tonumber(ARGV[3])
local orderNo = ARGV[4]

local orderJson = redis.call('GET', detailKey)
if not orderJson then
    return {1}
end

local order = cjson.decode(orderJson)
if tostring(order['userId']) ~= tostring(userId) then
    return {2}
end
if order['status'] ~= 'PENDING_PAYMENT' then
    return {3, order['status'] or ''}
end

order['status'] = 'CANCELLED'
order['cancelledAt'] = nowIso
order['cancelledAtEpochMs'] = nowMs
order['updatedAt'] = nowIso
order['updatedAtEpochMs'] = nowMs
order['version'] = tonumber(order['version'] or 1) + 1

local updatedJson = cjson.encode(order)
local itemJson = redis.call('GET', itemKey) or '[]'
local dirtyScore = tonumber(order['createdAtEpochMs'] or nowMs) or nowMs
redis.call('SET', detailKey, updatedJson)
redis.call('ZADD', dirtyKey, dirtyScore, orderNo)

return {0, updatedJson, itemJson}
