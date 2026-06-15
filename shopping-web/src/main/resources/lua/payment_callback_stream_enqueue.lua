local stream_key = KEYS[1]
local dedupe_key = KEYS[2]

local callback_no = ARGV[1]
local order_no = ARGV[2]
local external_trade_no = ARGV[3]
local payment_provider = ARGV[4]
local paid_at_epoch_ms = ARGV[5]
local paid_amount_yuan = ARGV[6]
local idempotency_key = ARGV[7]
local raw_payload_json = ARGV[8]
local received_at_epoch_ms = ARGV[9]
local dedupe_ttl_seconds = tonumber(ARGV[10])

local existing_callback_no = redis.call('GET', dedupe_key)
if existing_callback_no then
    return {existing_callback_no, '0', ''}
end

local stream_id = redis.call(
        'XADD',
        stream_key,
        '*',
        'callbackNo',
        callback_no,
        'orderNo',
        order_no,
        'externalTradeNo',
        external_trade_no,
        'paymentProvider',
        payment_provider,
        'paidAtEpochMs',
        paid_at_epoch_ms,
        'paidAmountYuan',
        paid_amount_yuan,
        'idempotencyKey',
        idempotency_key,
        'rawPayloadJson',
        raw_payload_json,
        'receivedAtEpochMs',
        received_at_epoch_ms
)
redis.call('SET', dedupe_key, callback_no, 'EX', dedupe_ttl_seconds)
return {callback_no, '1', stream_id}
