local detailKey = KEYS[1]
local itemKey = KEYS[2]
local dirtyKey = KEYS[3]
local closingKey = KEYS[4]

local nowIso = ARGV[1]
local nowMs = tonumber(ARGV[2])
local orderNo = ARGV[3]

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
if order['status'] ~= 'CLOSING' then
    return {2, order['status'] or ''}
end
if tonumber(order['closingDeadlineAtEpochMs'] or 0) > nowMs then
    return {3}
end

local orderUserId = user_id_text(order['userId'])
if orderUserId ~= '' then
    order['userId'] = orderUserId
end
order['status'] = 'CLOSED'
order['closedAt'] = nowIso
order['closedAtEpochMs'] = nowMs
order['updatedAt'] = nowIso
order['updatedAtEpochMs'] = nowMs
order['version'] = tonumber(order['version'] or 1) + 1

local updatedJson = cjson.encode(order)
local itemJson = redis.call('GET', itemKey) or '[]'
redis.call('SET', detailKey, updatedJson)
redis.call('ZADD', dirtyKey, nowMs, orderNo)
redis.call('ZREM', closingKey, orderNo)

return {0, updatedJson, itemJson}
