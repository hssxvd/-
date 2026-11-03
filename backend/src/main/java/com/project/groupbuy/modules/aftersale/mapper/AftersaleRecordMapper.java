package com.project.groupbuy.modules.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.aftersale.entity.AftersaleRecord;
import com.project.groupbuy.modules.aftersale.vo.AftersaleRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AftersaleRecord Mapper（售后处理轨迹）
 */
@Mapper
public interface AftersaleRecordMapper extends BaseMapper<AftersaleRecord> {

    /**
     * 查询某售后单的处理轨迹（按时间正序，供前端时间线渲染）
     * 注意：自定义 @Select 不走逻辑删除插件，需手写 is_deleted = 0
     */
    @Select("SELECT id, aftersale_id, from_status, to_status, operator_id, operator_role, remark, create_time " +
            "FROM aftersale_record " +
            "WHERE is_deleted = 0 AND aftersale_id = #{aftersaleId} " +
            "ORDER BY create_time ASC, id ASC")
    List<AftersaleRecordVO> listByAftersaleId(@Param("aftersaleId") Long aftersaleId);
}
