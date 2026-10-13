package com.luminawalk;

import com.luminawalk.entity.Shop;
import com.luminawalk.service.impl.ShopServiceImpl;
import com.luminawalk.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.luminawalk.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class LuminaWalkApplicationTests {
    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }
}
