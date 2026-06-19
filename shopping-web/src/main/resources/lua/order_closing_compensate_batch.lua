local closingKey = KEYS[1]
local dirtyKey = KEYS[2]

local nowIso = ARGV[1]
local nowMs = tonumber(ARGV[2])
local batchSize = tonumber(ARGV[3])
local detailKeyPrefix = ARGV[4]
local itemKeyPrefix = ARGV[5]

if not batchSize or batchSize <= 0 then
    return {0, 0, 0, 0, 0, 0}
end

local function user_id_text(value)
    if value == nil then
        return ''
    end
    if type(value) == 'number' then
        return string.format('%.0f', value)
    end
    return tostring(value)
end

local function is_terminal(status)
    return status == 'PAID' or status == 'CANCELLED' or status == 'CLOSED'
end

local orderNos = redis.call('ZRANGEBYSCORE', closingKey, '-inf', nowMs, 'LIMIT', 0, batchSize)
local result = {#orderNos, 0, 0, 0, 0, 0}
local changedCount = 0
local staleMissingCount = 0
local staleTerminalCount = 0
local skippedNonClosingCount = 0
local skippedNotDueCount = 0

for _, orderNo in ipairs(orderNos) do
    local detailKey = detailKeyPrefix .. orderNo
    local itemKey = itemKeyPrefix .. orderNo
    local orderJson = redis.call('GET', detailKey)

    if not orderJson then
        redis.call('ZREM', closingKey, orderNo)
        staleMissingCount = staleMissingCount + 1
    else
        local order = cjson.decode(orderJson)
        local status = order['status']
        if is_terminal(status) then
            redis.call('ZREM', closingKey, orderNo)
            staleTerminalCount = staleTerminalCount + 1
        elseif status ~= 'CLOSING' then
            skippedNonClosingCount = skippedNonClosingCount + 1
        else
            local deadlineMs = tonumber(order['closingDeadlineAtEpochMs'] or 0)
            if deadlineMs > nowMs then
                redis.call('ZADD', closingKey, deadlineMs, orderNo)
                skippedNotDueCount = skippedNotDueCount + 1
            else
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

                changedCount = changedCount + 1
                table.insert(result, orderNo)
                table.insert(result, updatedJson)
                table.insert(result, itemJson)
            end
        end
    end
end

result[2] = changedCount
result[3] = staleMissingCount
result[4] = staleTerminalCount
result[5] = skippedNonClosingCount
result[6] = skippedNotDueCount

return result
