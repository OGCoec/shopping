local pattern = ARGV[1]
local scanCount = tonumber(ARGV[2] or '500')
if not pattern or pattern == '' then
    return {0}
end
if not scanCount or scanCount <= 0 then
    scanCount = 500
end

local cursor = '0'
local deleted = 0

repeat
    local result = redis.call('SCAN', cursor, 'MATCH', pattern, 'COUNT', scanCount)
    cursor = result[1]
    local keys = result[2]
    local size = #keys
    local offset = 1
    while offset <= size do
        local batch = {}
        local batchSize = 0
        while offset <= size and batchSize < 500 do
            batchSize = batchSize + 1
            batch[batchSize] = keys[offset]
            offset = offset + 1
        end
        if batchSize > 0 then
            deleted = deleted + redis.call('DEL', unpack(batch))
        end
    end
until cursor == '0'

return {deleted}
