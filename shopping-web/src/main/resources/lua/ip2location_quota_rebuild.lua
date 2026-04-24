-- KEYS[1] = total count key，例如 ip2location:quota:count
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:
--
-- 扫描所有额度 key，求和后原子覆盖 count key。
-- 整个过程在 Lua 中完成，不会被 decrementQuota 等操作打断。

local totalKey = KEYS[1]
local prefix   = ARGV[1]

local cursor = "0"
local total  = 0

repeat
    local result = redis.call('SCAN', cursor, 'MATCH', prefix .. '*', 'COUNT', 100)
    cursor = result[1]
    local keys = result[2]

    for _, key in ipairs(keys) do
        if key ~= totalKey then
            local keyType = redis.call('TYPE', key)['ok']
            if keyType == 'string' then
                local val = redis.call('GET', key)
                if val ~= false then
                    local num = tonumber(val)
                    if num ~= nil then
                        total = total + num
                    end
                end
            end
        end
    end
until cursor == "0"

redis.call('SET', totalKey, total)

return {0, tostring(total)}
