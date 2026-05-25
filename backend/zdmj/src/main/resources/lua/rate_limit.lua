-- KEYS[1]  限流 key
-- ARGV[1]  窗口大小（毫秒）
-- ARGV[2]  窗口内最大请求数
local key = KEYS[1]
local windowMs = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])

local count = redis.call('INCR', key)
if count == 1 then
  redis.call('PEXPIRE', key, windowMs)
end

if count <= limit then
  return 1
end
return 0