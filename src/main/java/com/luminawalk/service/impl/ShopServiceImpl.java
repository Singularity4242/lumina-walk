package com.luminawalk.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.luminawalk.dto.Result;
import com.luminawalk.entity.Shop;
import com.luminawalk.mapper.ShopMapper;
import com.luminawalk.service.IShopService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.luminawalk.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByID(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)) {
            // 3.存在，不为空从redis取
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 4.存在，为空
        if(shopJson != null) {
            return Result.fail("店铺信息不存在");
        }
        // 5.redis不存在,从数据库查
        Shop shop = getById(id);
        // 6.数据库不存在，返回错误
        if(shop == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺信息不存在");
        }
        // 7.数据库存在，返回，写进redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL );
        return Result.ok(shop);
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("id不能为空");
        }
        // 先数据库更新
        updateById(shop);
        // 再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
