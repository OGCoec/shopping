local dirtyKey = KEYS[1]
local expireKey = KEYS[2]
local closingKey = KEYS[3]

local callbacks = cjson.decode(ARGV[1])
local results = {}

local function text(value)
    if value == nil then
        return ''
    end
    return tostring(value)
end

local function reason(status)
    if status == 'CLOSED' then
        return 'PAID_AFTER_ORDER_CLOSED'
    end
    if status == 'CANCELLED' then
        return 'PAID_AFTER_ORDER_CANCELLED'
    end
    return 'OTHER'
end

for index, callback in ipairs(callbacks) do
    local detailKey = KEYS[index + 3]
    local orderJson = redis.call('GET', detailKey)
    local result = {
        callbackNo = callback['callbackNo'],
        orderNo = callback['orderNo'],
        externalTradeNo = callback['externalTradeNo'],
        paymentProvider = callback['paymentProvider'],
        paidAtEpochMs = callback['paidAtEpochMs'],
        paidAmountYuan = callback['paidAmountYuan'],
        changed = false
    }

    if not orderJson then
        result['outcome'] = 'MISSING'
        result['orderStatus'] = 'NOT_FOUND'
        table.insert(results, result)
    else
        local order = cjson.decode(orderJson)
        local status = text(order['status'])
        result['userId'] = order['userId']
        result['userCouponId'] = order['userCouponId']
        result['userCouponIdHex'] = order['userCouponIdHex']
        result['totalAmountYuan'] = order['totalAmountYuan']
        result['discountAmountYuan'] = order['discountAmountYuan']
        result['payAmountYuan'] = order['payAmountYuan']

        if status == 'PENDING_PAYMENT' or status == 'CLOSING' then
            order['status'] = 'PAID'
            order['paidAt'] = callback['paidAt']
            order['paidAtEpochMs'] = callback['paidAtEpochMs']
            if callback['externalTradeNo'] and callback['externalTradeNo'] ~= '' then
                order['externalTradeNo'] = callback['externalTradeNo']
            end
            order['updatedAt'] = callback['paidAt']
            order['updatedAtEpochMs'] = callback['paidAtEpochMs']
            order['version'] = tonumber(order['version'] or 1) + 1

            local updatedJson = cjson.encode(order)
            redis.call('SET', detailKey, updatedJson)
            redis.call('ZADD', dirtyKey, callback['paidAtEpochMs'], callback['orderNo'])
            redis.call('ZREM', expireKey, callback['orderNo'])
            redis.call('ZREM', closingKey, callback['orderNo'])

            result['outcome'] = 'PAID'
            result['orderStatus'] = 'PAID'
            result['changed'] = true
            table.insert(results, result)
        elseif status == 'PAID' then
            redis.call('ZADD', dirtyKey, callback['paidAtEpochMs'], callback['orderNo'])
            redis.call('ZREM', expireKey, callback['orderNo'])
            redis.call('ZREM', closingKey, callback['orderNo'])

            result['outcome'] = 'PAID_IDEMPOTENT'
            result['orderStatus'] = 'PAID'
            table.insert(results, result)
        else
            result['outcome'] = 'REFUND_PENDING'
            result['orderStatus'] = status == '' and 'UNKNOWN' or status
            result['reasonCode'] = reason(result['orderStatus'])
            table.insert(results, result)
        end
    end
end

return cjson.encode(results)
