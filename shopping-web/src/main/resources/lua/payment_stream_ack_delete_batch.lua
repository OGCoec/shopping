local stream_key = KEYS[1]
local group_name = ARGV[1]

local count = 0
for i = 2, #ARGV do
    local message_id = ARGV[i]
    redis.call('XACK', stream_key, group_name, message_id)
    redis.call('XDEL', stream_key, message_id)
    count = count + 1
end
return count
