package com.project.groupbuy.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
