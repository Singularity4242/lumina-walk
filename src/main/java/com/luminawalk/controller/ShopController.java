package com.luminawalk.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminawalk.dto.Result;
import com.luminawalk.entity.Shop;
import com.luminawalk.service.IShopService;
import com.luminawalk.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;
import cn.hutool.core.util.StrUtil;

import javax.annotation.Resource;


@RestController
@RequestMapping("/shop")
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
        return Result.ok(shopService.getById(id));
    }

    @GetMapping("/of/type")
    public  Result queryShopByType(@RequestParam("typeID") Integer typeID,
                                   @RequestParam(value = "current", defaultValue = "1") Integer current){
        Page<Shop> page = shopService.query()
                .eq("type_id", typeID)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @GetMapping("/of/name")
    public  Result queryShopByName(@RequestParam(value = "name", required = false) String name,
                                   @RequestParam(value = "current", defaultValue = "1") Integer current){
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name),"name", name)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
