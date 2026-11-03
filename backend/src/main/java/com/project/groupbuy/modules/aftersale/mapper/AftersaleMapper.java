package com.project.groupbuy.modules.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.constant.AftersaleStatusConstant;
import com.project.groupbuy.modules.aftersale.dto.AftersaleQueryRequest;
import com.project.groupbuy.modules.aftersale.entity.Aftersale;
import com.project.groupbuy.modules.aftersale.vo.AftersaleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * Aftersale Mapper（v1.14 售后管理）
 *
 * 列表/详情共用同一段 SELECT 列清单（AFTERSALE_COLUMNS），
 * 保证列表与详情的字段口径完全一致（约束 11 统计口径统一）。
 *
 * 注意：
 *   1. 自定义 @Select 不走 MyBatis-Plus 逻辑删除插件，必须手写 a.is_deleted = 0；
 *   2. 注解 SQL 走 XML 解析，比较符号必须转义（&lt; / &gt;）；
 *   3. 状态/类型字符串一律引用 AftersaleStatusConstant，禁止裸串。
 */
@Mapper
public interface AftersaleMapper extends BaseMapper<Aftersale> {

    /** 售后列表/详情共用列清单（含订单、明细、用户、商家快照）*/
    String AFTERSALE_COLUMNS =
            "a.id, a.aftersale_no, a.order_id, a.order_no, a.order_item_id, a.user_id, a.merchant_id, " +
            "a.aftersale_type, a.reason, a.description, a.images, a.apply_amount, a.approved_amount, " +
            "a.aftersale_status, a.audit_operator_id, a.audit_time, a.audit_remark, " +
            "a.refund_no, a.refund_time, a.create_time, " +
            "o.order_status, o.aftersale_status AS order_aftersale_status, " +
            "o.pay_amount, o.refunded_amount AS order_refunded_amount, " +
            "oi.product_name, oi.sku_name, oi.spec, oi.image, oi.quantity, oi.subtotal, " +
            "oi.refunded_qty, oi.refunded_amount AS item_refunded_amount, " +
            "u.nickname AS user_nickname, m.merchant_name ";

    /** 售后列表/详情共用 FROM + JOIN */
    String AFTERSALE_FROM =
            "FROM aftersale a " +
            "LEFT JOIN orders o ON a.order_id = o.id " +
            "LEFT JOIN order_item oi ON a.order_item_id = oi.id " +
            "LEFT JOIN `user` u ON a.user_id = u.id " +
            "LEFT JOIN merchant m ON a.merchant_id = m.id ";

    /** 售后列表/详情共用动态筛选条件 */
    String AFTERSALE_WHERE =
            "WHERE a.is_deleted = 0 " +
            "<if test='merchantId != null'> AND a.merchant_id = #{merchantId} </if>" +
            "<if test='userId != null'> AND a.user_id = #{userId} </if>" +
            "<if test='q.orderId != null'> AND a.order_id = #{q.orderId} </if>" +
            "<if test='q.aftersaleStatus != null and q.aftersaleStatus != \"\"'> AND a.aftersale_status = #{q.aftersaleStatus} </if>" +
            "<if test='q.aftersaleType != null and q.aftersaleType != \"\"'> AND a.aftersale_type = #{q.aftersaleType} </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'> AND (a.aftersale_no LIKE CONCAT('%', #{q.keyword}, '%') " +
            "  OR a.order_no LIKE CONCAT('%', #{q.keyword}, '%')) </if>" +
            "<if test='q.startDate != null'> AND a.create_time &gt;= #{q.startDate} </if>" +
            "<if test='q.endDate != null'> AND a.create_time &lt; DATE_ADD(#{q.endDate}, INTERVAL 1 DAY) </if>";

    /**
     * 售后分页列表（消费者 / 商家 / Admin 三端共用）
     *
     * @param merchantId 商家强制隔离条件（商家端由 SecurityContext 注入，Admin 端可空）
     * @param userId     消费者强制隔离条件（消费者端由 SecurityContext 注入，其余端为 null）
     */
    @Select("<script>SELECT " + AFTERSALE_COLUMNS + AFTERSALE_FROM + AFTERSALE_WHERE +
            "ORDER BY a.create_time DESC, a.id DESC</script>")
    IPage<AftersaleVO> pageAftersale(Page<AftersaleVO> page,
                                     @Param("q") AftersaleQueryRequest q,
                                     @Param("merchantId") Long merchantId,
                                     @Param("userId") Long userId);

    /**
     * 售后详情（单行，列口径与列表一致）
     */
    @Select("SELECT " + AFTERSALE_COLUMNS + AFTERSALE_FROM +
            "WHERE a.is_deleted = 0 AND a.id = #{id}")
    AftersaleVO getDetail(@Param("id") Long id);

    /**
     * 售后状态聚合统计（约束 11：统计卡数据必须由后端统计接口提供）
     *
     * 口径（文档化，禁止前端二次计算）：
     *   total           售后单总数
     *   applying        待审核（APPLYING）
     *   approved        待退款（APPROVED，瞬时态，一般恒为 0）
     *   rejected        已驳回（REJECTED）
     *   refunding       退款中（REFUNDING，瞬时态，一般恒为 0）
     *   refunded        已退款（REFUNDED）
     *   refundedAmount  已退款金额合计（仅 REFUNDED 单的 approved_amount）
     *   pendingCount    待处理 = applying + approved
     *
     * @param merchantId 为空时统计全平台（Admin 端），否则只统计该商家
     */
    @Select("<script>SELECT COUNT(1) AS total, " +
            "COALESCE(SUM(CASE WHEN a.aftersale_status = '" + AftersaleStatusConstant.APPLYING + "' THEN 1 ELSE 0 END), 0) AS applying, " +
            "COALESCE(SUM(CASE WHEN a.aftersale_status = '" + AftersaleStatusConstant.APPROVED + "' THEN 1 ELSE 0 END), 0) AS approved, " +
            "COALESCE(SUM(CASE WHEN a.aftersale_status = '" + AftersaleStatusConstant.REJECTED + "' THEN 1 ELSE 0 END), 0) AS rejected, " +
            "COALESCE(SUM(CASE WHEN a.aftersale_status = '" + AftersaleStatusConstant.REFUNDING + "' THEN 1 ELSE 0 END), 0) AS refunding, " +
            "COALESCE(SUM(CASE WHEN a.aftersale_status = '" + AftersaleStatusConstant.REFUNDED + "' THEN 1 ELSE 0 END), 0) AS refunded, " +
            "COALESCE(SUM(CASE WHEN a.aftersale_status = '" + AftersaleStatusConstant.REFUNDED + "' THEN a.approved_amount ELSE 0 END), 0) AS refunded_amount " +
            "FROM aftersale a " +
            "<where> a.is_deleted = 0 <if test='merchantId != null'> AND a.merchant_id = #{merchantId} </if></where>" +
            "</script>")
    Map<String, Object> statsByMerchant(@Param("merchantId") Long merchantId);
}
