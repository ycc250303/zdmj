local stored = redis.call('GET', KEYS[1])
if not stored then
  return 0
end

if ARGV[1] == 'cancel' then
  -- 邮件失败时核对完整记录，避免删除其他发送请求的验证码。
  if stored ~= ARGV[2] then
    return 0
  end
elseif ARGV[1] == 'verify' then
  -- 记录格式为发送标识:六位验证码，校验与删除在同一脚本内完成。
  if string.sub(stored, -7) ~= ':' .. ARGV[2] then
    return -1
  end
else
  return redis.error_reply('Unknown verification code action')
end

return redis.call('DEL', KEYS[1])
