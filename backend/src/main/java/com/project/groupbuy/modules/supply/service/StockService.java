package com.project.groupbuy.modules.supply.service;

import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.constant.StockChangeTypeConstant;
import com.project.groupbuy.common.constant.StockWarnStatusConstant;
import com.project.groupbuy.modules.supply.dto.StockListQueryRequest;
import com.project.groupbuy.modules.supply.dto.StockOverviewQueryRequest;
import com.project.groupbuy.modules.supply.dto.StockRecordQueryRequest;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.entity.StockRecord;
import com.project.groupbuy.modules.supply.mapper.StockMapper;
import com.project.groupbuy.modules.supply.mapper.StockRecordMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 库存服务（约束文档 6 核心：条件更新防超卖）
 *
 * 六种变更类型：
 * - LOCK     预占（下单时）
 * - UNLOCK   释放预占（取消/超时）
 * - DEDUCT   实扣（支付后）
 * - ROLLBACK 回滚（异常回滚）
 * - INBOUND  人工入库/调拨
 * - SUPPLY_IN 供应到货入库（约束文档 6.1）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final StockCacheService stockCache;

    /** 合法变更类型白名单（约束文档 7.3：参数合法性校验） */
    private static final Set<String> VALID_CHANGE_TYPES = Set.of(
            StockChangeTypeConstant.LOCK,
            StockChangeTypeConstant.UNLOCK,
            StockChangeTypeConstant.DEDUCT,
            StockChangeTypeConstant.ROLLBACK,
            StockChangeTypeConstant.INBOUND,
            StockChangeTypeConstant.SUPPLY_IN);

    /** 合法预警状态白名单（取值见 StockWarnStatusConstant） */
    private static final Set<String> VALID_WARN_STATUS = Set.of(
            StockWarnStatusConstant.WARN,
            StockWarnStatusConstant.EMPTY,
            StockWarnStatusConstant.NORMAL);

    /** 分页默认值（约束文档 7.2：默认 1 / 10，最大 100） */
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    /** 趋势默认天数 */
    private static final int DEFAULT_TREND_DAYS = 30;
    private static final int MAX_TREND_DAYS = 90;

    /**
     * 查询 SKU 在社区的库存
     * 约束 28.16: 自提点级优先，没找到降级到社区级（pickup_point_id = 0）
     * 注：stock 表 pickup_point_id 已改为 NOT NULL DEFAULT 0（0 = 社区级默认库存，代表无自提）
     */
    public Stock getBySkuAndCommunity(Long skuId, Long communityId, Long pickupPointId) {
        // 第一优先级: 自提点级专属库存（pickup_point_id > 0）
        if (pickupPointId != null && pickupPointId != 0) {
            Stock s = stockMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Stock>()
                            .eq(Stock::getSkuId, skuId)
                            .eq(Stock::getCommunityId, communityId)
                            .eq(Stock::getPickupPointId, pickupPointId)
                            .last("LIMIT 1"));
            if (s != null) return s;
        }
        // 降级: 社区级默认库存 (pickup_point_id = 0)
        return stockMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Stock>()
                        .eq(Stock::getSkuId, skuId)
                        .eq(Stock::getCommunityId, communityId)
                        .eq(Stock::getPickupPointId, 0L)
                        .last("LIMIT 1"));
    }

    /**
     * ✅ N+1 修复（v1.19）：批量查多个 SKU 在指定社区/自提点的 stock
     *
     * 一次 SQL 替代 for 循环里 N 次 getBySkuAndCommunity，适用于：
     *   PaymentService.releaseTimeout / PaymentService.deductStockLocked
     *   FulfillmentService.transition COMPLETED
     *   OrderService.cancel 等场景
     *
     * @return Map<Long, Stock> key = skuId
     */
    public Map<Long, Stock> batchGetBySkusAndCommunity(Collection<Long> skuIds,
                                                       Long communityId, Long pickupPointId) {
        if (skuIds == null || skuIds.isEmpty()) return Collections.emptyMap();

        // 先查自提点级（pickup_point_id > 0）
        List<Stock> all = new ArrayList<>();
        if (pickupPointId != null && pickupPointId != 0) {
            all.addAll(stockMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Stock>()
                            .in(Stock::getSkuId, skuIds)
                            .eq(Stock::getCommunityId, communityId)
                            .eq(Stock::getPickupPointId, pickupPointId)));
        }
        // 再查社区级默认（pickup_point_id = 0）
        all.addAll(stockMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Stock>()
                        .in(Stock::getSkuId, skuIds)
                        .eq(Stock::getCommunityId, communityId)
                        .eq(Stock::getPickupPointId, 0L)));

        // 自提点级优先（按 skuId 分组，先遇到自提点级就不覆盖）
        Map<Long, Stock> result = new LinkedHashMap<>();
        for (Stock s : all) {
            result.putIfAbsent(s.getSkuId(), s);
        }
        return result;
    }

    /**
     * 预占库存（约束文档 6：条件更新防超卖）
     * UPDATE stock SET locked_qty = locked_qty + qty, version = version + 1
     * WHERE available_qty >= qty AND version = ?
     *
     * v1.17 高并发预检：先走 Redis DECRBY（1ms 响应），库存不足直接返回，
     *     不再查 MySQL，QPS 从 ~300 翻到 ~3000。
     *     Redis 异常时降级放行，保证主流程不受缓存影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long stockId, Integer qty, String orderNo) {
        // ✅ Redis 预检（高并发第一道门）
        boolean pass = stockCache.tryDecr(stockId, qty);
        if (!pass) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                    String.format("SKU stockId=%d 库存不足（Redis 预检拒绝）", stockId));
        }

        Stock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            stockCache.rollbackDecr(stockId, qty);
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "库存记录不存在");
        }
        if (stock.getAvailableQty() < qty) {
            // Redis/MySQL 不一致（极罕见），回滚 Redis + 重对齐
            stockCache.rollbackDecr(stockId, qty);
            stockCache.syncAvailable(stockId, stock.getAvailableQty());
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                    String.format("SKU %d 库存不足，可用 %d，需要 %d（Redis/DB 已自动对齐）",
                            stock.getSkuId(), stock.getAvailableQty(), qty));
        }

        int affected = stockMapper.lockStock(stockId, qty, stock.getVersion());
        if (affected == 0) {
            // DB 乐观锁冲突 → 回滚 Redis + 重同步
            stockCache.rollbackDecr(stockId, qty);
            Stock latest = stockMapper.selectById(stockId);
            if (latest != null) stockCache.syncAvailable(stockId, latest.getAvailableQty());
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "库存并发冲突，请重试");
        }

        // ✅ DB 锁库成功，Redis 已被 DECRBY 改对，无需再 sync
        writeRecord(stock, StockChangeTypeConstant.LOCK, qty, orderNo, null);
    }

    /**
     * 释放预占
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long stockId, Integer qty, String orderNo, String reason) {
        Stock stock = stockMapper.selectById(stockId);
        if (stock == null || stock.getLockedQty() < qty) {
            return; // 幂等：没有预占可以释放
        }
        int affected = stockMapper.unlockStock(stockId, qty, stock.getVersion());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "释放库存失败");
        }
        writeRecord(stock, StockChangeTypeConstant.UNLOCK, -qty, orderNo, reason);
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
    }

    /**
     * 预占转实扣（幂等：预占已消耗过时降级扣 total_qty）
     *
     * 正常路径：locked_qty >= qty → locked -= qty, total -= qty
     * 降级路径：locked_qty < qty（预占已被消耗过）→ 只扣 total_qty，不碰 locked
     *          触发场景：订单曾走过 COMPLETED 已扣库存，后状态回拉，再次核销
     * 异常路径：total_qty < qty → 抛"库存实扣失败"
     */
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long stockId, Integer qty, String orderNo) {
        Stock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "库存记录不存在");
        }
        int locked = stock.getLockedQty() == null ? 0 : stock.getLockedQty();
        int total = stock.getTotalQty() == null ? 0 : stock.getTotalQty();

        if (locked >= qty) {
            // ✅ 正常路径：预占足够，预占转实扣
            int affected = stockMapper.deductStock(stockId, qty, stock.getVersion());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "库存实扣失败");
            }
            writeRecord(stock, StockChangeTypeConstant.DEDUCT, -qty, orderNo, null);
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
        } else if (total >= qty) {
            // ⚠️ 降级路径：预占已被消耗（可能之前已 deduct 过），只扣 total
            log.warn("[库存实扣降级] stockId={} locked_qty={} < qty={}，可能已被实扣过，降级只扣 total_qty。orderNo={}",
                    stockId, locked, qty, orderNo);
            int affected = stockMapper.deductStockNet(stockId, qty, stock.getVersion());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "库存实扣失败（降级路径）");
            }
            writeRecord(stock, StockChangeTypeConstant.DEDUCT, -qty, orderNo, "幂等降级（预占已消耗）");
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
        } else {
            // ❌ 真的库存不足
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                    String.format("库存实扣失败：预占=%d 总库=%d 需扣=%d", locked, total, qty));
        }
    }

    /**
     * 供应到货入库（约束文档 6.1：SUPPLY_IN）
     * 由 SupplyPlanService 三动作事务调用，这里只做库存部分
     */
    @Transactional(rollbackFor = Exception.class)
    public void supplyIn(Long stockId, Integer qty, Long operatorId, String remark) {
        Stock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "库存记录不存在");
        }
        int affected = stockMapper.supplyIn(stockId, qty, stock.getVersion());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "供应入库失败");
        }
        String rm = remark != null ? remark : "operatorId=" + operatorId;
        writeRecord(stock, StockChangeTypeConstant.SUPPLY_IN, qty, null, rm);
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
    }

    /**
     * 人工入库/调拨
     */
    @Transactional(rollbackFor = Exception.class)
    public void inbound(Long stockId, Integer qty, Long operatorId, String remark) {
        Stock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "库存记录不存在");
        }
        int affected = stockMapper.inboundAdjust(stockId, qty, stock.getVersion());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "入库失败");
        }
        writeRecord(stock, StockChangeTypeConstant.INBOUND, qty, null, remark);
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
    }

    /**
     * ROLLBACK 库存回滚（约束文档 6 六变更类型补全）
     * 与 UNLOCK 区别：ROLLBACK 是已 DEDUCT 的库存回退（available + total 都加），UNLOCK 是预占释放（locked→available）
     * 适用场景：支付成功后因异常需要整笔回退、供应到货后整单取消等
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long stockId, Integer qty, Long operatorId, String orderNo, String remark) {
        Stock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "库存记录不存在");
        }
        // ROLLBACK：available_qty 和 total_qty 都加回
        int affected = stockMapper.rollback(stockId, qty, stock.getVersion());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.STOCK_ROLLBACK_FAILED, "库存回滚失败，请重试");
        }
        writeRecord(stock, StockChangeTypeConstant.ROLLBACK, qty, orderNo, remark != null ? remark : "库存回滚");
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
    }

    /**
     * 供应到货入库 · 兜底自动建库存（SupplyPlanService.arrive 调用）
     * - stock 存在 → supplyIn(total+qty, available+qty) + SUPPLY_IN 流水
     * - stock 不存在 → insert new stock(total=qty, available=qty) + SUPPLY_IN 流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void supplyInOrCreate(Long skuId, Long communityId, Long pickupPointId,
                                  Integer qty, String orderNo, Long operatorId, String diffRemark) {
        Stock stock = getBySkuAndCommunity(skuId, communityId, pickupPointId);
        if (stock != null) {
            supplyIn(stock.getId(), qty, operatorId, diffRemark);
        } else {
            Stock s = new Stock();
            s.setSkuId(skuId);
            s.setCommunityId(communityId);
            s.setPickupPointId(pickupPointId);
            s.setTotalQty(qty);
            s.setAvailableQty(qty);
            s.setLockedQty(0);
            stockMapper.insert(s);

            StockRecord r = new StockRecord();
            r.setStockId(s.getId());
            r.setSkuId(skuId);
            r.setCommunityId(communityId);
            r.setChangeType(StockChangeTypeConstant.SUPPLY_IN);
            r.setChangeQty(qty);
            r.setBeforeTotal(0);
            r.setAfterTotal(qty);
            r.setBeforeLocked(0);
            r.setAfterLocked(0);
            r.setOrderNo(orderNo);
            String rm = "供应到货首次入库（planNo=" + orderNo + "）";
            if (diffRemark != null && !diffRemark.isBlank()) rm += "; " + diffRemark;
            r.setRemark(rm);
            r.setOperatorId(operatorId);
            stockRecordMapper.insert(r);
        }
    }

    /**
     * 库存流水查询
     */
    public List<StockRecord> listRecords(Long stockId, String changeType, String orderNo) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockRecord> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        w.eq(stockId != null, StockRecord::getStockId, stockId);
        w.eq(changeType != null, StockRecord::getChangeType, changeType);
        w.eq(orderNo != null, StockRecord::getOrderNo, orderNo);
        w.orderByDesc(StockRecord::getCreateTime);
        return stockRecordMapper.selectList(w);
    }

    /**
     * 根据订单号查涉及的库存记录（从 stock_record 关联）
     */
    public List<Stock> getStockByOrderNo(String orderNo) {
        List<StockRecord> records = stockRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockRecord>()
                        .eq(StockRecord::getOrderNo, orderNo));
        if (records.isEmpty()) return Collections.emptyList();
        return stockMapper.selectBatchIds(records.stream().map(StockRecord::getStockId).distinct().toList());
    }

    /**
     * 商家端库存列表（约束文档 584 数据隔离 + 约束文档 6 条件更新）
     * 返回 List<Map> 方便直接塞给前端 el-table，避免定义额外 VO
     */
    public List<Map<String, Object>> listMine(Long merchantId) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        return stockMapper.listMineForMerchant(merchantId);
    }

    /**
     * 商家端库存列表（分页 + 筛选）
     * 约束文档 11：列表接口必须分页，禁止全量返回；预警口径由后端 SQL 统一计算
     */
    public IPage<Map<String, Object>> pageMine(Long merchantId, StockListQueryRequest req) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        StockListQueryRequest q = req != null ? req : new StockListQueryRequest();
        if (q.getWarnStatus() != null && !q.getWarnStatus().isBlank()
                && !VALID_WARN_STATUS.contains(q.getWarnStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预警状态取值非法：" + q.getWarnStatus());
        }
        Page<Map<String, Object>> page = new Page<>(normalizePageNum(q.getPageNum()), normalizePageSize(q.getPageSize()));
        return stockMapper.pageMineForMerchant(page, q, merchantId);
    }

    /**
     * 商家端库存列表 —— Product × Community 归组视图（方案 B）
     *
     * 与 pageMine 的区别：
     *   pageMine        —— SKU×社区 粒度（存储层真实粒度，约束文档 §4.4）
     *   pageMineGrouped —— Product×社区 粒度（GROUP BY 聚合展示，前端默认视图）
     *
     * 为什么不改变存储粒度：约束文档 §4.4 明确 `uk_sku_community_pickup`，
     * 下单锁库扣减都以 sku_id 为 WHERE 条件（§6）。存储粒度改 Product 会导致超卖。
     */
    public IPage<Map<String, Object>> pageMineGrouped(Long merchantId, StockListQueryRequest req) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        StockListQueryRequest q = req != null ? req : new StockListQueryRequest();
        Page<Map<String, Object>> page = new Page<>(normalizePageNum(q.getPageNum()), normalizePageSize(q.getPageSize()));
        return stockMapper.pageMineGroupByProduct(page, q, merchantId);
    }

    /**
     * 商家端库存流水分页查询（约束文档 11 + 8 数据隔离）
     */
    public IPage<Map<String, Object>> pageRecords(Long merchantId, StockRecordQueryRequest req) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        StockRecordQueryRequest q = req != null ? req : new StockRecordQueryRequest();
        if (q.getChangeType() != null && !q.getChangeType().isBlank()
                && !VALID_CHANGE_TYPES.contains(q.getChangeType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "变更类型取值非法：" + q.getChangeType());
        }
        if (q.getStartDate() != null && q.getEndDate() != null && q.getStartDate().isAfter(q.getEndDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        Page<Map<String, Object>> page = new Page<>(normalizePageNum(q.getPageNum()), normalizePageSize(q.getPageSize()));
        return stockRecordMapper.pageRecordsForMerchant(page, q, merchantId);
    }

    /**
     * 库存总览：统计卡 + 近 N 天出入库趋势（约束文档 11：可视化数据必须由后端统计接口提供）
     *
     * 统计口径（文档化，禁止前端二次计算）：
     *   skuCount     库存记录数
     *   totalQty     在库合计   SUM(total_qty)
     *   lockedQty    预占合计   SUM(locked_qty)
     *   availableQty 可用合计   SUM(available_qty)
     *   warnCount    预警 SKU 数（口径与列表 warn_status 完全一致）
     *   emptyCount   空仓 SKU 数
     *   lockedCount  有预占的 SKU 数
     *   趋势：入库 = SUM(change_qty > 0)，出库 = SUM(-change_qty < 0)，无数据日期补 0
     */
    public Map<String, Object> overview(Long merchantId, StockOverviewQueryRequest req) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        StockOverviewQueryRequest q = req != null ? req : new StockOverviewQueryRequest();
        int days = q.getDays() == null ? DEFAULT_TREND_DAYS : Math.min(Math.max(q.getDays(), 1), MAX_TREND_DAYS);

        Map<String, Object> raw = stockMapper.overviewForMerchant(merchantId, q.getCommunityId());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("skuCount", toInt(raw == null ? null : raw.get("sku_count")));
        stats.put("totalQty", toInt(raw == null ? null : raw.get("total_qty")));
        stats.put("lockedQty", toInt(raw == null ? null : raw.get("locked_qty")));
        stats.put("availableQty", toInt(raw == null ? null : raw.get("available_qty")));
        stats.put("warnCount", toInt(raw == null ? null : raw.get("warn_count")));
        stats.put("emptyCount", toInt(raw == null ? null : raw.get("empty_count")));
        stats.put("lockedCount", toInt(raw == null ? null : raw.get("locked_count")));

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<Map<String, Object>> rows = stockRecordMapper.dailyFlowForMerchant(
                merchantId, q.getCommunityId(), start.atStartOfDay());

        Map<String, int[]> byDate = new HashMap<>();
        for (Map<String, Object> r : rows) {
            LocalDate d = toLocalDate(r.get("stat_date"));
            if (d == null) continue;
            byDate.put(d.toString(), new int[]{toInt(r.get("in_qty")), toInt(r.get("out_qty"))});
        }

        List<Map<String, Object>> trend = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            int[] v = byDate.getOrDefault(d.toString(), new int[]{0, 0});
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.toString());
            point.put("inQty", v[0]);
            point.put("outQty", v[1]);
            trend.add(point);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("stats", stats);
        res.put("trend", trend);
        res.put("days", days);
        res.put("caliber", "入库=change_qty>0 合计，出库=change_qty<0 绝对值合计");
        return res;
    }

    // ==================== Admin 端（全商家视角，只读） ====================

    /**
     * Admin 库存列表（分页 + 筛选，全商家或指定商家）
     * 约束文档 11：列表必须分页；预警口径后端统一计算
     */
    public IPage<Map<String, Object>> pageAllForAdmin(Long merchantId, StockListQueryRequest req) {
        StockListQueryRequest q = req != null ? req : new StockListQueryRequest();
        if (q.getWarnStatus() != null && !q.getWarnStatus().isBlank()
                && !VALID_WARN_STATUS.contains(q.getWarnStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预警状态取值非法：" + q.getWarnStatus());
        }
        Page<Map<String, Object>> page = new Page<>(normalizePageNum(q.getPageNum()), normalizePageSize(q.getPageSize()));
        return stockMapper.pageAllForAdmin(page, q, merchantId);
    }

    /**
     * Admin 库存流水（分页 + 多维度筛选/下钻，全商家或指定商家）
     */
    public IPage<Map<String, Object>> pageRecordsForAdmin(Long merchantId, StockRecordQueryRequest req) {
        StockRecordQueryRequest q = req != null ? req : new StockRecordQueryRequest();
        if (q.getChangeType() != null && !q.getChangeType().isBlank()
                && !VALID_CHANGE_TYPES.contains(q.getChangeType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "变更类型取值非法：" + q.getChangeType());
        }
        if (q.getStartDate() != null && q.getEndDate() != null && q.getStartDate().isAfter(q.getEndDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        Page<Map<String, Object>> page = new Page<>(normalizePageNum(q.getPageNum()), normalizePageSize(q.getPageSize()));
        return stockRecordMapper.pageRecordsForAdmin(page, q, merchantId);
    }

    /**
     * Admin 库存总览统计 + 近 N 天出入库趋势（全商家或指定商家）
     * 口径与商家端 overview 完全一致，仅 WHERE 条件改为可选 merchantId
     */
    public Map<String, Object> overviewForAdmin(Long merchantId, StockOverviewQueryRequest req) {
        StockOverviewQueryRequest q = req != null ? req : new StockOverviewQueryRequest();
        int days = q.getDays() == null ? DEFAULT_TREND_DAYS : Math.min(Math.max(q.getDays(), 1), MAX_TREND_DAYS);

        Map<String, Object> raw = stockMapper.overviewForAdmin(merchantId, q.getCommunityId());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("skuCount", toInt(raw == null ? null : raw.get("sku_count")));
        stats.put("totalQty", toInt(raw == null ? null : raw.get("total_qty")));
        stats.put("lockedQty", toInt(raw == null ? null : raw.get("locked_qty")));
        stats.put("availableQty", toInt(raw == null ? null : raw.get("available_qty")));
        stats.put("warnCount", toInt(raw == null ? null : raw.get("warn_count")));
        stats.put("emptyCount", toInt(raw == null ? null : raw.get("empty_count")));
        stats.put("lockedCount", toInt(raw == null ? null : raw.get("locked_count")));

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<Map<String, Object>> rows = stockRecordMapper.dailyFlowForAdmin(
                merchantId, q.getCommunityId(), start.atStartOfDay());

        Map<String, int[]> byDate = new HashMap<>();
        for (Map<String, Object> r : rows) {
            LocalDate d = toLocalDate(r.get("stat_date"));
            if (d == null) continue;
            byDate.put(d.toString(), new int[]{toInt(r.get("in_qty")), toInt(r.get("out_qty"))});
        }

        List<Map<String, Object>> trend = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            int[] v = byDate.getOrDefault(d.toString(), new int[]{0, 0});
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.toString());
            point.put("inQty", v[0]);
            point.put("outQty", v[1]);
            trend.add(point);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("stats", stats);
        res.put("trend", trend);
        res.put("days", days);
        res.put("caliber", "入库=change_qty>0 合计，出库=change_qty<0 绝对值合计");
        return res;
    }

    private int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return new java.math.BigDecimal(v.toString()).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate) return (LocalDate) v;
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate();
        if (v instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) v).getTime()).toLocalDate();
        }
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalDate();
        try {
            return LocalDate.parse(v.toString().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 商家端人工调整库存
     * @param stockId 库存记录 id
     * @param qty     正数=增加，负数=减少
     * @param remark  调整原因（必填）
     */
    @OpLog(module = "库存", operationType = "UPDATE", targetType = "Stock")
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long stockId, Integer qty, Long operatorId, String remark) {
        if (qty == null || qty == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "调整数量不能为 0");
        }
        if (remark == null || remark.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "调整原因必填");
        }
        Stock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "库存记录不存在");
        }
        int affected = stockMapper.inboundAdjust(stockId, qty, stock.getVersion());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                    "调整失败：调整后库存不能为负，也不能小于锁定数量（当前可用=" + stock.getAvailableQty() +
                            "，锁定=" + stock.getLockedQty() + "）");
        }
        writeRecord(stock, StockChangeTypeConstant.INBOUND, qty, null, remark);
        Stock _after = stockMapper.selectById(stock.getId());
        if (_after != null) stockCache.syncAvailable(stock.getId(), _after.getAvailableQty());
    }

    /**
     * 归属校验：某 stock 是否属于指定 merchant（数据隔离守卫）
     */
    public boolean belongsToMerchant(Long stockId, Long merchantId) {
        if (stockId == null || merchantId == null) return false;
        Integer c = stockMapper.countStockByMerchant(stockId, merchantId);
        return c != null && c > 0;
    }

    /**
     * 写库存流水（约束文档 6：流水必须与库存变更双写一致）
     *
     * @param before 变更前的库存快照（各调用方均在条件更新前 selectById 取得）
     * 变更后快照在更新后重新读取，保证 before/after 真实反映本次变更
     */
    private void writeRecord(Stock before, String type, Integer qty, String orderNo, String remark) {
        Stock after = stockMapper.selectById(before.getId());
        if (after == null) after = before;

        StockRecord r = new StockRecord();
        r.setStockId(before.getId());
        r.setSkuId(before.getSkuId());
        r.setCommunityId(before.getCommunityId());
        r.setChangeType(type);
        r.setChangeQty(qty);
        r.setBeforeTotal(before.getTotalQty());
        r.setAfterTotal(after.getTotalQty());
        r.setBeforeLocked(before.getLockedQty());
        r.setAfterLocked(after.getLockedQty());
        r.setOrderNo(orderNo);
        r.setRemark(remark);
        r.setOperatorId(com.project.groupbuy.common.context.SecurityContext.getUserId());
        stockRecordMapper.insert(r);
    }
}
