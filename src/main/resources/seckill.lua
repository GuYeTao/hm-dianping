-- 参数列表
-- 优惠券
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]

-- 数据key
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 脚本
-- 库存是否充足
if(tonumber(redis.call('get', stockKey))<=0) then
    return 1
end
-- 判断是否下单
if(redis.call('sismember', orderKey, userId)==1) then
    return 2
end
-- 扣库存，下单
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0