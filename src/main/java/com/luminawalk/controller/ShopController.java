package com.luminawalk.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminawalk.dto.Result;
import com.luminawalk.utils.SlidingWindowLimit;
import com.luminawalk.entity.Shop;
import com.luminawalk.service.IShopService;
import com.luminawalk.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/shop")
@SlidingWindowLimit(globalLimit = true)
public class ShopController {

    @Resource
    public IShopService shopService;

    @PostMapping
    public Result saveShop(@RequestBody Shop shop){
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    @GetMapping("/of/type")
    public  Result queryShopByType(@RequestParam("typeId") Integer typeId,
                                   @RequestParam(value = "current", defaultValue = "1") Integer current,
                                   @RequestParam(value = "x", required = false) Double x,
                                   @RequestParam(value = "y", required = false) Double y){
        log.info("根据商铺类型分页查询商铺信息，type:{},x:{},y:{},当前页数：{},",typeId,x,y,current);
        return shopService.queryShopByType(typeId, current, x, y);
    }

    @GetMapping("/of/name")
    public  Result queryShopByName(@RequestParam(value = "name", required = false) String name,
                                   @RequestParam(value = "current", defaultValue = "1") Integer current){
        log.info("根据商铺类型分页查询商铺信息，name:{}",name);
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name),"name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }
}
