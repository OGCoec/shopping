-- KEYS[1] = total count key，例如 ip2location:quota:count
-- KEYS[2] = index set key，例如 ip2location:quota:index
-- ARGV[1] = quota key 前缀，例如 ip2location:quota:
--
-- 通过索引 Set 获取所有额度 key，求和后原子覆盖 count key。
-- Set 为空时用 SCAN 兜底补注册（兼容迁移场景）。

local totalKey = KEYS[1]
local indexKey = KEYS[2]
local prefix   = ARGV[1]

local allKeys = redis.call('SMEMBERS', indexKey)

-- 兼容迁移：Set 为空时用 SCAN 补注册
if #allKeys == 0 then
    local cursor = "0"
    repeat
        local result = redis.call('SCAN', cursor, 'MATCH', prefix .. '*', 'COUNT', 100)
        cursor = result[1]
        local keys = result[2]
        for _, key in ipairs(keys) do
            if key ~= totalKey and key ~= indexKey then
                local keyType = redis.call('TYPE', key)['ok']
                if keyType == 'string' then
                    local val = redis.call('GET', key)
                    if val ~= false and tonumber(val) ~= nil then
                        redis.call('SADD', indexKey, key)
                        table.insert(allKeys, key)
                    end
                end
            end
        end
    until cursor == "0"
end

local total = 0

if #allKeys > 0 then
    local allValues = redis.call('MGET', unpack(allKeys))
    for i = 1, #allKeys do
        local val = allValues[i]
        if val == false then
            redis.call('SREM', indexKey, allKeys[i])
        else
            local num = tonumber(val)
            if num ~= nil then
                total = total + num
            end
        end
    end
end

redis.call('SET', totalKey, total)

return {0, tostring(total)}
