package com.project.groupbuy.modules.aftersale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.constant.AftersaleStatusConstant;
import com.project.groupbuy.common.constant.OrderStatusConstant;
import com.project.groupbuy.common.constant.UserTypeConstant;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.aftersale.dto.AftersaleApplyRequest;
import com.project.groupbuy.modules.aftersale.dto.AftersaleQueryRequest;
import com.project.groupbuy.modules.aftersale.entity.Aftersale;
import com.project.groupbuy.modules.aftersale.entity.AftersaleRecord;
import com.project.groupbuy.modules.aftersale.mapper.AftersaleMapper;
import com.project.groupbuy.modules.aftersale.mapper.AftersaleRecordMapper;
import com.project.groupbuy.modules.aftersale.vo.AftersaleDetailVO;
import com.project.groupbuy.modules.aftersale.vo.AftersaleRecordVO;
import com.project.groupbuy.modules.aftersale.vo.AftersaleVO;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.entity.OrderStatusLog;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.order.mapper.OrderStatusLogMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.payment.service.PaymentService;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 售后服务（约束文档 5.5 售后状态机 + 5.2 订单状态机 + 6 库存回库 + 11 统计口径）
 *
 * 一、售后单据状态机（aftersale.aftersale_status）
 *   APPLYING → APPROVED | REJECTED
 *   APPROVED → REFUNDING → REFUNDED
 *
 * 二、订单售后聚合状态（orders.aftersale_status）
 *   NONE → APPLYING → REFUNDING → REFUNDED；驳回回落 NONE
 *
 * 三、审核通过（单事务内 9 步，任一失败整体回滚）
 *   ① APPLYING → APPROVED
 *   ② APPROVED → REFUNDING + 订单聚合 REFUNDING
 *   ③ PaymentService.refund()（幂等：bizNo = aftersaleNo）
 *   ④ order_item.refunded_qty / refunded_amount 回填
 *   ⑤ RETURN 类型 → StockService.inbound() 库存回库
 *   ⑥ REFUNDING → REFUNDED + 订单聚合 REFUNDED
 *   ⑦ 订单主状态：全额退款且原状态非 COMPLETED → REFUNDING → REFUNDED
 *      （COMPLETED 订单保持 COMPLETED，售后进度由 aftersale_status 表达；部分退款不改主状态）
 *   ⑧ aftersale_record 轨迹落库
 *   ⑨ order_status_log(status_type=AFTERSALE) + @OpLog 操作日志
 *
 * 四、约束（约束文档 5.5）
 *   apply_amount ≤ order_item.subtotal − order_item.refunded_amount
 *   累计退款 ≤ orders.pay_amount
 *   同一 order_item 有效售后单 ≤ 2 张
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AftersaleService {

    private final AftersaleMapper aftersaleMapper;
    private final AftersaleRecordMapper aftersaleRecordMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final ProductMapper productMapper;

    /** 分页默认值（约束 7.2：默认 1 / 10，最大 100）*/
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    /** 同一明细有效售后单上限（约束 5.5）*/
    private static final int MAX_AFTERSALE_PER_ITEM = 2;
    /** 系统操作者角色（轨迹里由系统自动完成的节点）*/
    private static final String ROLE_SYSTEM = "SYSTEM";

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ================================================================
    // 一、消费者申请售后
    // ================================================================

    /**
     * 消费者申请售后（APPLYING）
     * 约束 5.5：仅已支付且未进入终态的订单可申请；同一明细有效售后 ≤ 2
     */
    @OpLog(module = "售后", operationType = "CREATE", targetType = "Aftersale")
    @Transactional(rollbackFor = Exception.class)
    public AftersaleDetailVO apply(AftersaleApplyRequest req) {
        Long userId = requireUserId();

        // ① 类型白名单（约束 7.3 参数合法性校验）
        if (req.getType() == null || !AftersaleStatusConstant.VALID_TYPES.contains(req.getType())) {
            throw new BusinessException(ErrorCode.AFTERSALE_TYPE_INVALID, "售后类型非法：" + req.getType());
        }

        // ② 订单校验 + 归属校验
        Orders order = ordersMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }

        // ③ 订单主状态准入：未支付 / 已取消 / 退款中 / 已退款 均不可申请
        String os = order.getOrderStatus();
        if (OrderStatusConstant.PENDING_PAY.equals(os) || OrderStatusConstant.isTerminal(os)) {
            throw new BusinessException(ErrorCode.AFTERSALE_INVALID,
                    "订单当前状态不允许申请售后：" + OrderStatusConstant.label(os));
        }
        // 已有退款在途，禁止重复申请
        if (AftersaleStatusConstant.ORDER_REFUNDING.equals(order.getAftersaleStatus())) {
            throw new BusinessException(ErrorCode.AFTERSALE_INVALID, "该订单退款处理中，请等待完成");
        }

        // ④ 明细校验（必须属于该订单）
        OrderItem oi = orderItemMapper.selectById(req.getOrderItemId());
        if (oi == null || !oi.getOrderId().equals(order.getId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单明细不存在");
        }

        // ⑤ 同一明细有效售后单 ≤ 2（约束 5.5）
        Long active = aftersaleMapper.selectCount(
                new LambdaQueryWrapper<Aftersale>()
                        .eq(Aftersale::getOrderItemId, oi.getId())
                        .in(Aftersale::getAftersaleStatus, AftersaleStatusConstant.ACTIVE_STATUS));
        if (active != null && active >= MAX_AFTERSALE_PER_ITEM) {
            throw new BusinessException(ErrorCode.AFTERSALE_COUNT_EXCEED,
                    "同一商品明细售后次数已达上限（" + MAX_AFTERSALE_PER_ITEM + " 次）");
        }

        // ⑥ 金额校验：单明细可退额 + 订单累计实付额双重守卫
        BigDecimal itemRefunded = nz(oi.getRefundedAmount());
        BigDecimal maxRefund = oi.getSubtotal().subtract(itemRefunded);
        if (maxRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.AFTERSALE_AMOUNT_INVALID, "该商品明细已无可退金额");
        }
        if (req.getApplyAmount().compareTo(maxRefund) > 0) {
            throw new BusinessException(ErrorCode.AFTERSALE_AMOUNT_INVALID,
                    "申请金额超过该明细可退金额（可退 " + maxRefund + " 元）");
        }
        BigDecimal orderRefunded = nz(order.getRefundedAmount());
        if (orderRefunded.add(req.getApplyAmount()).compareTo(order.getPayAmount()) > 0) {
            throw new BusinessException(ErrorCode.REFUND_EXCEED, "累计退款金额超过订单实付金额");
        }

        // ⑦ 落库
        Aftersale a = new Aftersale();
        a.setAftersaleNo(generateAftersaleNo());
        a.setOrderId(order.getId());
        a.setOrderNo(order.getOrderNo());
        a.setOrderItemId(oi.getId());
        a.setUserId(userId);
        a.setMerchantId(order.getMerchantId());
        a.setAftersaleType(req.getType());
        a.setReason(req.getReason());
        a.setDescription(req.getDescription());
        a.setImages(req.getImages());
        a.setApplyAmount(req.getApplyAmount());
        a.setAftersaleStatus(AftersaleStatusConstant.APPLYING);
        aftersaleMapper.insert(a);

        // ⑧ 轨迹 + 订单聚合状态 + 订单日志
        writeRecord(a.getId(), null, AftersaleStatusConstant.APPLYING, userId,
                UserTypeConstant.CONSUMER, "消费者提交售后申请：" + req.getReason());
        changeOrderAftersaleStatus(order, AftersaleStatusConstant.ORDER_APPLYING, userId,
                "申请售后：" + a.getAftersaleNo());

        log.info("[售后申请] aftersaleNo={}, orderNo={}, type={}, amount={}",
                a.getAftersaleNo(), order.getOrderNo(), req.getType(), req.getApplyAmount());
        return loadDetail(a.getId());
    }

    // ================================================================
    // 二、审核（商家 / Admin）
    // ================================================================

    /**
     * 商家审核（约束 5.5：通过 → 退款联动；驳回 → 订单聚合回落 NONE）
     */
    @OpLog(module = "售后", operationType = "AUDIT", targetType = "Aftersale")
    @Transactional(rollbackFor = Exception.class)
    public AftersaleDetailVO audit(Long aftersaleId, boolean approve, String remark) {
        Long merchantId = SecurityContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        return doAudit(aftersaleId, approve, remark, merchantId, UserTypeConstant.MERCHANT);
    }

    /**
     * Admin 强制审核（v1.14：Admin 只读监控 + 可强制介入）
     * 与商家审核同一套状态机与事务步骤，仅不校验商家归属。
     */
    @OpLog(module = "售后", operationType = "AUDIT", targetType = "Aftersale")
    @Transactional(rollbackFor = Exception.class)
    public AftersaleDetailVO adminAudit(Long aftersaleId, boolean approve, String remark) {
        return doAudit(aftersaleId, approve, remark, null, UserTypeConstant.ADMIN);
    }

    /**
     * Admin 强制退款（v1.14）
     * 允许对 APPLYING（跳过商家审核）或 REJECTED（推翻驳回）的售后单直接退款；
     * 已退款 / 退款中 拒绝重复操作（幂等守卫）。
     */
    @OpLog(module = "售后", operationType = "REFUND", targetType = "Aftersale")
    @Transactional(rollbackFor = Exception.class)
    public AftersaleDetailVO forceRefund(Long aftersaleId, String reason) {
        Aftersale a = requireAftersale(aftersaleId);
        String status = a.getAftersaleStatus();
        if (AftersaleStatusConstant.REFUNDED.equals(status)) {
            throw new BusinessException(ErrorCode.AFTERSALE_INVALID, "该售后单已退款，请勿重复操作");
        }
        if (AftersaleStatusConstant.REFUNDING.equals(status)) {
            throw new BusinessException(ErrorCode.AFTERSALE_INVALID, "该售后单退款处理中，请稍后");
        }

        Long operatorId = SecurityContext.getUserId();
        if (a.getApprovedAmount() == null) {
            a.setApprovedAmount(a.getApplyAmount());
        }
        a.setAuditOperatorId(operatorId);
        a.setAuditTime(LocalDateTime.now());
        a.setAuditRemark("平台强制介入：" + reason);
        a.setAftersaleStatus(AftersaleStatusConstant.APPROVED);
        aftersaleMapper.updateById(a);
        writeRecord(a.getId(), status, AftersaleStatusConstant.APPROVED, operatorId,
                UserTypeConstant.ADMIN, "平台强制审核通过：" + reason);

        log.warn("[售后强制退款] aftersaleNo={}, 原状态={}, reason={}", a.getAftersaleNo(), status, reason);
        return completeRefund(a, operatorId, UserTypeConstant.ADMIN, reason);
    }

    private AftersaleDetailVO doAudit(Long aftersaleId, boolean approve, String remark,
                                       Long requireMerchantId, String operatorRole) {
        Aftersale a = requireAftersale(aftersaleId);
        if (requireMerchantId != null && !requireMerchantId.equals(a.getMerchantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权处理该售后单");
        }
        if (!AftersaleStatusConstant.APPLYING.equals(a.getAftersaleStatus())) {
            throw new BusinessException(ErrorCode.AFTERSALE_INVALID,
                    "仅待审核（APPLYING）的售后单可审核，当前状态：" + AftersaleStatusConstant.label(a.getAftersaleStatus()));
        }

        Long operatorId = SecurityContext.getUserId();

        // ---------- 驳回 ----------
        if (!approve) {
            a.setAftersaleStatus(AftersaleStatusConstant.REJECTED);
            a.setAuditOperatorId(operatorId);
            a.setAuditTime(LocalDateTime.now());
            a.setAuditRemark(remark);
            aftersaleMapper.updateById(a);
            writeRecord(a.getId(), AftersaleStatusConstant.APPLYING, AftersaleStatusConstant.REJECTED,
                    operatorId, operatorRole, "驳回：" + (remark == null ? "" : remark));

            Orders order = ordersMapper.selectById(a.getOrderId());
            if (order != null) {
                changeOrderAftersaleStatus(order, AftersaleStatusConstant.ORDER_NONE, operatorId,
                        "驳回售后申请：" + a.getAftersaleNo());
            }
            log.info("[售后驳回] aftersaleNo={}, remark={}", a.getAftersaleNo(), remark);
            return loadDetail(aftersaleId);
        }

        // ---------- 通过 ----------
        a.setApprovedAmount(a.getApplyAmount());
        a.setAuditOperatorId(operatorId);
        a.setAuditTime(LocalDateTime.now());
        a.setAuditRemark(remark);
        a.setAftersaleStatus(AftersaleStatusConstant.APPROVED);
        aftersaleMapper.updateById(a);
        writeRecord(a.getId(), AftersaleStatusConstant.APPLYING, AftersaleStatusConstant.APPROVED,
                operatorId, operatorRole, "审核通过：" + (remark == null ? "" : remark));

        return completeRefund(a, operatorId, operatorRole, remark);
    }

    /**
     * 审核通过后的退款闭环（② ~ ⑦，与申请/审核同一事务）
     */
    private AftersaleDetailVO completeRefund(Aftersale a, Long operatorId, String operatorRole, String reason) {
        Long orderId = a.getOrderId();

        // ② APPROVED → REFUNDING + 订单聚合 REFUNDING
        a.setAftersaleStatus(AftersaleStatusConstant.REFUNDING);
        aftersaleMapper.updateById(a);
        writeRecord(a.getId(), AftersaleStatusConstant.APPROVED, AftersaleStatusConstant.REFUNDING,
                operatorId, operatorRole, "进入退款流程");
        Orders order = ordersMapper.selectById(orderId);
        if (order != null) {
            changeOrderAftersaleStatus(order, AftersaleStatusConstant.ORDER_REFUNDING, operatorId,
                    "退款处理中：" + a.getAftersaleNo());
        }

        // ③ 执行退款前的二次可退额校验（约束 5.5）：
        //    审核通过发生在申请之后，期间可能已被其他售后单退掉同一明细；
        //    Admin 强制退款还可能推翻更早的 REJECTED 单据（其申请时的校验已过期）。
        //    若此时余额不足必须拦截，否则会重复退款导致 refunded_amount > pay_amount。
        BigDecimal approved = nz(a.getApprovedAmount());
        OrderItem oi = a.getOrderItemId() == null ? null : orderItemMapper.selectById(a.getOrderItemId());
        if (oi != null) {
            BigDecimal itemMax = nz(oi.getSubtotal()).subtract(nz(oi.getRefundedAmount()));
            if (approved.compareTo(itemMax) > 0) {
                throw new BusinessException(ErrorCode.AFTERSALE_AMOUNT_INVALID,
                        "明细可退金额不足（剩余 " + itemMax + " 元），请刷新后重试");
            }
        }
        Orders latestOrder = ordersMapper.selectById(orderId);
        if (latestOrder != null) {
            BigDecimal orderMax = nz(latestOrder.getPayAmount()).subtract(nz(latestOrder.getRefundedAmount()));
            if (approved.compareTo(orderMax) > 0) {
                throw new BusinessException(ErrorCode.AFTERSALE_AMOUNT_INVALID,
                        "订单可退金额不足（剩余 " + orderMax + " 元），请刷新后重试");
            }
        }

        // ④ 退款（幂等：bizNo = aftersaleNo，重复调用不会重复累加）
        String refundReason = (reason == null || reason.isBlank() ? "" : reason + "；") + "售后单 " + a.getAftersaleNo();
        String refundNo = paymentService.refund(orderId, a.getApprovedAmount(), refundReason, a.getAftersaleNo());
        a.setRefundNo(refundNo);
        a.setRefundTime(LocalDateTime.now());
        aftersaleMapper.updateById(a);

        // ⑤ 明细退款数量/金额回填
        if (oi != null) {
            oi.setRefundedQty(nzInt(oi.getRefundedQty()) + oi.getQuantity());
            oi.setRefundedAmount(nz(oi.getRefundedAmount()).add(a.getApprovedAmount()));
            orderItemMapper.updateById(oi);
        }

        // ⑥ 退货退款 → 库存回库（约束 5.5 + 6）
        if (AftersaleStatusConstant.TYPE_RETURN.equals(a.getAftersaleType()) && oi != null) {
            Orders o = ordersMapper.selectById(orderId);
            if (o != null) {
                Stock stock = stockService.getBySkuAndCommunity(oi.getSkuId(), o.getCommunityId(), o.getPickupPointId());
                if (stock != null) {
                    stockService.inbound(stock.getId(), oi.getQuantity(), operatorId,
                            "售后退货回库：" + a.getAftersaleNo());
                    log.info("[售后退货回库] aftersaleNo={}, stockId={}, qty={}",
                            a.getAftersaleNo(), stock.getId(), oi.getQuantity());
                } else {
                    log.warn("[售后退货回库] 未找到库存记录，跳过回库: aftersaleNo={}, skuId={}, communityId={}",
                            a.getAftersaleNo(), oi.getSkuId(), o.getCommunityId());
                }
            }
        }

        // ⑦ REFUNDING → REFUNDED + 订单聚合 REFUNDED
        a.setAftersaleStatus(AftersaleStatusConstant.REFUNDED);
        aftersaleMapper.updateById(a);
        writeRecord(a.getId(), AftersaleStatusConstant.REFUNDING, AftersaleStatusConstant.REFUNDED,
                null, ROLE_SYSTEM, "退款完成：" + refundNo);

        Orders latest = ordersMapper.selectById(orderId);
        if (latest != null) {
            changeOrderAftersaleStatus(latest, AftersaleStatusConstant.ORDER_REFUNDED, operatorId,
                    "退款完成：" + a.getAftersaleNo());
        }

        log.info("[售后完成] aftersaleNo={}, refundNo={}, amount={}",
                a.getAftersaleNo(), refundNo, a.getApprovedAmount());
        return loadDetail(a.getId());
    }

    // ================================================================
    // 三、查询（分页 + 统计 + 详情）
    // ================================================================

    /** 消费者：我的售后列表（分页）*/
    public IPage<AftersaleVO> pageMine(AftersaleQueryRequest req) {
        Long userId = requireUserId();
        AftersaleQueryRequest q = normalize(req);
        validateQuery(q);
        Page<AftersaleVO> page = new Page<>(q.getPageNum(), q.getPageSize());
        IPage<AftersaleVO> result = aftersaleMapper.pageAftersale(page, q, null, userId);
        fillLabels(result.getRecords());
        return result;
    }

    /** 商家：我的售后列表（分页，数据隔离）*/
    public IPage<AftersaleVO> pageByMerchant(AftersaleQueryRequest req) {
        Long merchantId = SecurityContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        AftersaleQueryRequest q = normalize(req);
        validateQuery(q);
        Page<AftersaleVO> page = new Page<>(q.getPageNum(), q.getPageSize());
        IPage<AftersaleVO> result = aftersaleMapper.pageAftersale(page, q, merchantId, null);
        fillLabels(result.getRecords());
        overrideAftersaleImages(result.getRecords());
        return result;
    }

    /** Admin：全平台售后列表（分页，可选按商家下钻）*/
    public IPage<AftersaleVO> pageForAdmin(AftersaleQueryRequest req) {
        AftersaleQueryRequest q = normalize(req);
        validateQuery(q);
        Page<AftersaleVO> page = new Page<>(q.getPageNum(), q.getPageSize());
        IPage<AftersaleVO> result = aftersaleMapper.pageAftersale(page, q, q.getMerchantId(), null);
        fillLabels(result.getRecords());
        overrideAftersaleImages(result.getRecords());
        return result;
    }

    /** 商家：售后统计卡（数据隔离）*/
    public Map<String, Object> statsByMerchant() {
        Long merchantId = SecurityContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        return buildStats(aftersaleMapper.statsByMerchant(merchantId), false);
    }

    /** Admin：全平台售后统计卡（可选按商家下钻）*/
    public Map<String, Object> statsForAdmin(Long merchantId) {
        return buildStats(aftersaleMapper.statsByMerchant(merchantId), true);
    }

    /**
     * 售后详情（按当前登录角色做归属校验）
     * 消费者 —— 仅本人；商家 —— 仅本商家；Admin —— 全平台
     */
    public AftersaleDetailVO detail(Long id) {
        AftersaleDetailVO vo = loadDetail(id);
        String userType = SecurityContext.getUserType();
        if (UserTypeConstant.isAdmin(userType)) {
            return vo;
        }
        if (UserTypeConstant.MERCHANT.equals(userType)) {
            Long merchantId = SecurityContext.getMerchantId();
            if (merchantId == null || !merchantId.equals(vo.getMerchantId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该售后单");
            }
            return vo;
        }
        if (!vo.getUserId().equals(SecurityContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该售后单");
        }
        return vo;
    }

    // ================================================================
    // 四、内部工具
    // ================================================================

    private AftersaleDetailVO loadDetail(Long id) {
        AftersaleVO base = aftersaleMapper.getDetail(id);
        if (base == null) {
            throw new BusinessException(ErrorCode.AFTERSALE_NOT_FOUND);
        }
        AftersaleDetailVO vo = new AftersaleDetailVO();
        BeanUtils.copyProperties(base, vo);
        List<AftersaleRecordVO> timeline = aftersaleRecordMapper.listByAftersaleId(id);
        fillTimelineLabels(timeline);
        vo.setTimeline(timeline);
        fillLabel(vo);
        overrideAftersaleImages(java.util.Collections.singletonList(vo));
        return vo;
    }

    private Aftersale requireAftersale(Long id) {
        Aftersale a = aftersaleMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ErrorCode.AFTERSALE_NOT_FOUND);
        }
        return a;
    }

    /**
     * 变更订单售后聚合状态（orders.aftersale_status）+ 写订单状态日志
     * 只动聚合字段，不改 order_status（主状态由 PaymentService.refund 负责）
     */
    private void changeOrderAftersaleStatus(Orders order, String status, Long operatorId, String reason) {
        if (order == null) return;
        order.setAftersaleStatus(status);
        ordersMapper.updateById(order);

        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(order.getId());
        l.setOrderNo(order.getOrderNo());
        l.setFromStatus(order.getOrderStatus());
        l.setToStatus(order.getOrderStatus());
        l.setStatusType("AFTERSALE");
        l.setOperatorId(operatorId);
        l.setReason(reason);
        orderStatusLogMapper.insert(l);
    }

    private void writeRecord(Long aftersaleId, String from, String to, Long operatorId,
                             String operatorRole, String remark) {
        AftersaleRecord r = new AftersaleRecord();
        r.setAftersaleId(aftersaleId);
        r.setFromStatus(from);
        r.setToStatus(to);
        r.setOperatorId(operatorId);
        r.setOperatorRole(operatorRole);
        r.setRemark(remark);
        aftersaleRecordMapper.insert(r);
    }

    private void fillLabels(List<AftersaleVO> list) {
        if (list == null) return;
        for (AftersaleVO vo : list) {
            fillLabel(vo);
        }
    }

    private void fillLabel(AftersaleVO vo) {
        if (vo == null) return;
        vo.setAftersaleStatusLabel(AftersaleStatusConstant.label(vo.getAftersaleStatus()));
        vo.setAftersaleTypeLabel(AftersaleStatusConstant.typeLabel(vo.getAftersaleType()));
        vo.setOrderStatusLabel(OrderStatusConstant.label(vo.getOrderStatus()));
    }
    /**
     * 售后管理图片实时统一：用 orderItemId → orderItem.productId → product.mainImage 覆盖 AftersaleVO.image。
     * DB order_item.image 快照保持不变（约束§4.3），仅覆盖返回给前端的展示值。
     */
    private void overrideAftersaleImages(List<AftersaleVO> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> itemIds = list.stream().map(AftersaleVO::getOrderItemId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (itemIds.isEmpty()) return;
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getId, itemIds));
        if (items.isEmpty()) return;
        Map<Long, Long> itemIdToProductId = items.stream().collect(java.util.stream.Collectors.toMap(OrderItem::getId, OrderItem::getProductId, (a, b) -> a));
        Set<Long> pids = items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (pids.isEmpty()) return;
        Map<Long, Product> pMap = productMapper.selectBatchIds(pids).stream().collect(java.util.stream.Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (AftersaleVO vo : list) {
            Long pid = itemIdToProductId.get(vo.getOrderItemId());
            if (pid != null) {
                Product p = pMap.get(pid);
                if (p != null && p.getMainImage() != null && !p.getMainImage().isBlank()) {
                    vo.setImage(p.getMainImage());
                }
            }
        }
    }

    private void fillTimelineLabels(List<AftersaleRecordVO> list) {
        if (list == null) return;
        for (AftersaleRecordVO r : list) {
            r.setFromStatusLabel(AftersaleStatusConstant.label(r.getFromStatus()));
            r.setToStatusLabel(AftersaleStatusConstant.label(r.getToStatus()));
            r.setOperatorRoleLabel(roleLabel(r.getOperatorRole()));
        }
    }

    private String roleLabel(String role) {
        if (role == null) return "-";
        if (ROLE_SYSTEM.equals(role)) return "系统";
        return UserTypeConstant.label(role);
    }

    private AftersaleQueryRequest normalize(AftersaleQueryRequest req) {
        AftersaleQueryRequest q = req != null ? req : new AftersaleQueryRequest();
        if (q.getPageNum() == null || q.getPageNum() < 1) q.setPageNum(DEFAULT_PAGE_NUM);
        if (q.getPageSize() == null || q.getPageSize() < 1) q.setPageSize(DEFAULT_PAGE_SIZE);
        if (q.getPageSize() > MAX_PAGE_SIZE) q.setPageSize(MAX_PAGE_SIZE);
        return q;
    }

    private void validateQuery(AftersaleQueryRequest q) {
        if (q.getAftersaleStatus() != null && !q.getAftersaleStatus().isBlank()
                && !AftersaleStatusConstant.VALID_STATUS.contains(q.getAftersaleStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "售后状态取值非法：" + q.getAftersaleStatus());
        }
        if (q.getAftersaleType() != null && !q.getAftersaleType().isBlank()
                && !AftersaleStatusConstant.VALID_TYPES.contains(q.getAftersaleType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "售后类型取值非法：" + q.getAftersaleType());
        }
        if (q.getStartDate() != null && q.getEndDate() != null && q.getStartDate().isAfter(q.getEndDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
    }

    /**
     * 售后统计口径（约束 11：统计卡数据必须由后端统一提供，前端禁止二次计算）
     *   total / applying / approved / rejected / refunding / refunded / refundedAmount
     *   pendingCount = applying + approved（待处理）
     */
    private Map<String, Object> buildStats(Map<String, Object> raw, boolean platformWide) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int applying = toInt(raw == null ? null : raw.get("applying"));
        int approved = toInt(raw == null ? null : raw.get("approved"));
        stats.put("total", toInt(raw == null ? null : raw.get("total")));
        stats.put("applying", applying);
        stats.put("approved", approved);
        stats.put("rejected", toInt(raw == null ? null : raw.get("rejected")));
        stats.put("refunding", toInt(raw == null ? null : raw.get("refunding")));
        stats.put("refunded", toInt(raw == null ? null : raw.get("refunded")));
        stats.put("refundedAmount", toDecimal(raw == null ? null : raw.get("refunded_amount")));
        stats.put("pendingCount", applying + approved);
        stats.put("scope", platformWide ? "全平台" : "本商家");
        stats.put("caliber", "待处理 = 待审核 + 待退款；已退款金额 = 已退款单的审核通过金额合计");
        return stats;
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return new BigDecimal(v.toString()).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private int nzInt(Integer v) {
        return v == null ? 0 : v;
    }

    private Long requireUserId() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 售后单号：AS + yyyyMMddHHmmss + 4 位随机（避免并发同秒碰撞）*/
    private String generateAftersaleNo() {
        return "AS" + NO_FORMAT.format(LocalDateTime.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
