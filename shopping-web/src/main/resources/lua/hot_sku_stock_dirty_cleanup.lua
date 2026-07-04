local dirtyKey = KEYS[1]

local itemCount = tonumber(ARGV[1] or '0')
if not itemCount or itemCount <= 0 then
    return {0, 0}
end

local removed = 0
local retained = 0

for index = 1, itemCount do
    local keyIndex = index + 1
    local argIndex = (index - 1) * 2 + 2
    local stockKey = KEYS[keyIndex]
    local skuId = ARGV[argIndex]
    local expectedRemaining = ARGV[argIndex + 1]
    local currentRemaining = redis.call('GET', stockKey)

    if currentRemaining == expectedRemaining then
        removed = removed + redis.call('SREM', dirtyKey, skuId)
    else
        retained = retained + 1
    end
end

return {removed, retained}
