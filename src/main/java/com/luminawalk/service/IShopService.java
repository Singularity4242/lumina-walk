package com.luminawalk.service;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

public interface IShopService extends IService<Shop> {

    Result queryByID(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
