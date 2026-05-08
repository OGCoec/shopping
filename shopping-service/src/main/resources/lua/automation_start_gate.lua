local device_block_key = KEYS[1]
local ip_block_key = KEYS[2]
local device_1s_key = KEYS[3]
local device_1m_key = KEYS[4]
local device_30m_key = KEYS[5]
local ip_1s_key = KEYS[6]
local ip_1m_key = KEYS[7]
local ip_30m_key = KEYS[8]

local device_enabled = ARGV[1] == '1'
local ip_enabled = ARGV[2] == '1'

local function pttl(key)
    local ttl = redis.call('PTTL', key)
    if ttl and ttl > 0 then
        return ttl
    end
    return 0
end

local existing_device_ttl_ms = 0
local existing_ip_ttl_ms = 0
if device_enabled then
    existing_device_ttl_ms = pttl(device_block_key)
end
if ip_enabled then
    existing_ip_ttl_ms = pttl(ip_block_key)
end

if existing_device_ttl_ms > 0 or existing_ip_ttl_ms > 0 then
    local retry_after_ms = math.max(existing_device_ttl_ms, existing_ip_ttl_ms)
    return {1, 'EXISTING_DEVICE_BLOCK', 'EXISTING_IP_BLOCK', retry_after_ms, 0, 0, existing_device_ttl_ms, existing_ip_ttl_ms, 0, 0, 0, 0, 0, 0}
end

local function incr_window(key, ttl_seconds)
    local count = redis.call('INCR', key)
    if count == 1 then
        redis.call('EXPIRE', key, ttl_seconds)
    end
    return count
end

local device_1s_count = 0
local device_1m_count = 0
local device_30m_count = 0
local ip_1s_count = 0
local ip_1m_count = 0
local ip_30m_count = 0

if device_enabled then
    device_1s_count = incr_window(device_1s_key, 1)
    device_1m_count = incr_window(device_1m_key, 60)
    device_30m_count = incr_window(device_30m_key, 1800)
end
if ip_enabled then
    ip_1s_count = incr_window(ip_1s_key, 1)
    ip_1m_count = incr_window(ip_1m_key, 60)
    ip_30m_count = incr_window(ip_30m_key, 1800)
end

local device_block_seconds = 0
local device_penalty = 0
local device_reason = ''
if device_enabled then
    if device_30m_count > tonumber(ARGV[15]) then
        device_block_seconds = tonumber(ARGV[16])
        device_penalty = tonumber(ARGV[17])
        device_reason = ARGV[18]
    elseif device_30m_count > tonumber(ARGV[11]) then
        device_block_seconds = tonumber(ARGV[12])
        device_penalty = tonumber(ARGV[13])
        device_reason = ARGV[14]
    elseif device_1m_count > tonumber(ARGV[7]) then
        device_block_seconds = tonumber(ARGV[8])
        device_penalty = tonumber(ARGV[9])
        device_reason = ARGV[10]
    elseif device_1s_count > tonumber(ARGV[3]) then
        device_block_seconds = tonumber(ARGV[4])
        device_penalty = tonumber(ARGV[5])
        device_reason = ARGV[6]
    end
end

local ip_block_seconds = 0
local ip_penalty = 0
local ip_reason = ''
if ip_enabled then
    if ip_30m_count > tonumber(ARGV[31]) then
        ip_block_seconds = tonumber(ARGV[32])
        ip_penalty = tonumber(ARGV[33])
        ip_reason = ARGV[34]
    elseif ip_30m_count > tonumber(ARGV[27]) then
        ip_block_seconds = tonumber(ARGV[28])
        ip_penalty = tonumber(ARGV[29])
        ip_reason = ARGV[30]
    elseif ip_1m_count > tonumber(ARGV[23]) then
        ip_block_seconds = tonumber(ARGV[24])
        ip_penalty = tonumber(ARGV[25])
        ip_reason = ARGV[26]
    elseif ip_1s_count > tonumber(ARGV[19]) then
        ip_block_seconds = tonumber(ARGV[20])
        ip_penalty = tonumber(ARGV[21])
        ip_reason = ARGV[22]
    end
end

local function apply_block(key, block_seconds)
    if block_seconds <= 0 then
        return 0
    end
    local current_ttl = redis.call('TTL', key)
    if current_ttl < block_seconds then
        redis.call('SET', key, '1', 'EX', block_seconds)
        return block_seconds * 1000
    end
    return current_ttl * 1000
end

local device_block_ttl_ms = 0
local ip_block_ttl_ms = 0
if device_enabled then
    device_block_ttl_ms = apply_block(device_block_key, device_block_seconds)
end
if ip_enabled then
    ip_block_ttl_ms = apply_block(ip_block_key, ip_block_seconds)
end

local blocked = 0
if device_block_ttl_ms > 0 or ip_block_ttl_ms > 0 then
    blocked = 1
end
local retry_after_ms = math.max(device_block_ttl_ms, ip_block_ttl_ms)

return {blocked, device_reason, ip_reason, retry_after_ms, device_penalty, ip_penalty, device_block_ttl_ms, ip_block_ttl_ms, device_1s_count, device_1m_count, device_30m_count, ip_1s_count, ip_1m_count, ip_30m_count}
