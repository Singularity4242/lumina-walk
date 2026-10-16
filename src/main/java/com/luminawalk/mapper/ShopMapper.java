package com.luminawalk.mapper;

import com.luminawalk.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
    /**
     * 分页查询所有店铺ID
     */
    @Select("SELECT id FROM tb_shop LIMIT #{offset}, #{limit}")
    List<Long> selectAllIds(@Param("offset") int offset, @Param("limit") int limit);
}
