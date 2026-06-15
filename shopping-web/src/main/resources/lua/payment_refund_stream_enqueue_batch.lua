local stream_key = KEYS[1]
local dedupe_prefix = ARGV[1]
local dedupe_ttl_seconds = tonumber(ARGV[2])

local result = {}
for i = 3, #ARGV do
    local refund_no = ARGV[i]
    if refund_no and refund_no ~= '' then
        local dedupe_key = dedupe_prefix .. refund_no
        local existing = redis.call('GET', dedupe_key)
        if not existing then
            local stream_id = redis.call(
                    'XADD',
                    stream_key,
                    '*',
                    'refundNo',
                    refund_no,
                    'enqueuedAtEpochMs',
                    redis.call('TIME')[1] .. '000'
            )
            redis.call('SET', dedupe_key, '1', 'EX', dedupe_ttl_seconds)
            table.insert(result, refund_no)
            table.insert(result, stream_id)
        end
    end
end
return result
