package com.sbtest.security.mapper;

import com.sbtest.security.entity.Menu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MenuMapper {
    List<Menu> getAllMenu();
}
