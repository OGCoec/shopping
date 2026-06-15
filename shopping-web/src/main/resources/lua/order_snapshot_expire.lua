local detailKey = KEYS[1]
local expireKey = KEYS[2]
local closingKey = KEYS[3]

local nowIso = ARGV[1]
local nowMs = tonumber(ARGV[2])
local orderNo = ARGV[3]
local closingDeadlineIso = ARGV[4]
local closingDeadlineMs = tonumber(ARGV[5])

local orderJson = redis.call('GET', detailKey)
if not orderJson then
    return {1}
end

local order = cjson.decode(orderJson)
if order['status'] ~= 'PENDING_PAYMENT' then
    return {2, order['status'] or ''}
end
if tonumber(order['expireAtEpochMs'] or 0) > nowMs then
    return {3}
end

order['status'] = 'CLOSING'
order['closingAt'] = nowIso
order['closingAtEpochMs'] = nowMs
order['closingDeadlineAt'] = closingDeadlineIso
order['closingDeadlineAtEpochMs'] = closingDeadlineMs
order['updatedAt'] = nowIso
order['updatedAtEpochMs'] = nowMs
order['version'] = tonumber(order['version'] or 1) + 1

local updatedJson = cjson.encode(order)
redis.call('SET', detailKey, updatedJson)
redis.call('ZREM', expireKey, orderNo)
redis.call('ZADD', closingKey, closingDeadlineMs, orderNo)

return {0, updatedJson, '[]'}
