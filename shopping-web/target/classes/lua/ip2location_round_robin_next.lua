-- KEYS[1] = round-robin cursor key, e.g. ip2location:round-robin:cursor
-- ARGV[1] = active quota key count

local cursorKey = KEYS[1]
local size = tonumber(ARGV[1])

if size == nil or size <= 0 then
    return 0
end

local rawCursor = tonumber(redis.call('GET', cursorKey) or '0') or 0
local startIndex = rawCursor % size
local nextCursor = (startIndex + 1) % size

redis.call('SET', cursorKey, nextCursor)

return startIndex
