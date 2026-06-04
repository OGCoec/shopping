local processingKey = KEYS[1]
local dirtyKey = KEYS[2]

local count = tonumber(ARGV[1])
if not count or count <= 0 then
    return 0
end

local orderNos = {}
local removed = 0
for index = 1, count do
    local orderNoIndex = 2 + (index - 1) * 2
    local scoreIndex = orderNoIndex + 1
    local orderNo = ARGV[orderNoIndex]
    local score = tonumber(ARGV[scoreIndex])
    if orderNo and orderNo ~= '' then
        orderNos[#orderNos + 1] = orderNo
        redis.call('ZADD', dirtyKey, score or 0, orderNo)
    end
end

if #orderNos > 0 then
    removed = redis.call('ZREM', processingKey, unpack(orderNos))
end

return removed
