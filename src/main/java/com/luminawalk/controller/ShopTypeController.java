package com.luminawalk.controller;


import com.luminawalk.dto.Result;
import com.luminawalk.utils.SlidingWindowLimit;
import com.luminawalk.entity.ShopType;
import com.luminawalk.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/shop-type")
@SlidingWindowLimit(globalLimit = true)
public class ShopTypeController {

    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        log.info("查询店铺清单");
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }

}
