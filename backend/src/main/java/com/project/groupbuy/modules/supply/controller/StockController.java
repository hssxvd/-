package com.project.groupbuy.modules.supply.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.supply.dto.StockListQueryRequest;
import com.project.groupbuy.modules.supply.dto.StockOverviewQueryRequest;
import com.project.groupbuy.modules.supply.dto.StockRecordQueryRequest;
import com.project.groupbuy.modules.supply.entity.StockRecord;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 商家端库存管理（约束文档 584 数据隔离 + 6 条件更新 + 11 分页与统计）
 *
 * 端点：
 *   GET    /supply/stocks/mine             — 我的库存列表（分页 + 预警状态筛选）
 *   GET    /supply/stocks/overview         — 库存总览（统计卡 + 近 N 天出入库趋势）
 *   GET    /supply/stocks/records          — 库存流水（分页 + 多维度筛选/下钻）
 *   GET    /supply/stocks/{id:\d+}/records — 单条库存流水
 *   POST   /supply/stocks/{id:\d+}/adjust  — 人工调整库存（写 operation_log）
 *
 * 数据隔离规则：stock 表没有 merchant_id，必须通过
 * stock → product_sku → product JOIN 确认归属
 */
@Slf4j
@RestController
@RequestMapping("/supply/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * GET /supply/stocks/mine — 商家库存列表（分页，SKU×社区 粒度）
     * 约束文档 11：列表接口必须分页；预警口径由后端统一计算（warn_status）
     */
    @GetMapping("/mine")
    public R<IPage<Map<String, Object>>> mine(@Valid StockListQueryRequest req) {
        return R.ok(stockService.pageMine(requireMerchantId(), req));
    }

    /**
     * GET /supply/stocks/mine/grouped — 按 Product×社区 归组视图（方案 B）
     *
     * 与 /mine 的区别：同一商品的多个 SKU 汇总成一行显示，状态取最严重级别。
     * 存储层仍严格 SKU 粒度（约束文档 §4.4），只有展示层做聚合。
     * 前端默认用这个端点，SKU 明细通过展开行显示。
     */
    @GetMapping("/mine/grouped")
    public R<IPage<Map<String, Object>>> mineGrouped(@Valid StockListQueryRequest req) {
        return R.ok(stockService.pageMineGrouped(requireMerchantId(), req));
    }

    /**
     * GET /supply/stocks/overview — 库存总览统计 + 近 N 天出入库趋势
     * 约束文档 11：可视化图表数据必须与后端统计接口一致
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@Valid StockOverviewQueryRequest req) {
        return R.ok(stockService.overview(requireMerchantId(), req));
    }

    /**
     * GET /supply/stocks/records — 库存流水分页查询
     * 筛选：changeType / orderNo / stockId / skuId / communityId / 日期区间
     */
    @GetMapping("/records")
    public R<IPage<Map<String, Object>>> records(@Valid StockRecordQueryRequest req) {
        return R.ok(stockService.pageRecords(requireMerchantId(), req));
    }

    /**
     * GET /supply/stocks/{id:\d+}/records — 单条库存流水
     * 约束 584 数据隔离：先校验归属再放行
     */
    @GetMapping("/{id:\\d+}/records")
    public R<List<StockRecord>> recordsOfStock(@PathVariable Long id,
                                               @RequestParam(required = false) String changeType,
                                               @RequestParam(required = false) String orderNo) {
        Long merchantId = requireMerchantId();
        if (!stockService.belongsToMerchant(id, merchantId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "库存记录不存在或无权限");
        }
        return R.ok(stockService.listRecords(id, changeType, orderNo));
    }

    /**
     * POST /supply/stocks/{id:\d+}/adjust — 人工调整库存
     * 约束 6 条件更新：inboundAdjust SQL 自带 version + >=0 守卫
     * 约束 7.3 敏感接口：@OpLog 记录操作日志
     */
    @PostMapping("/{id:\\d+}/adjust")
    public R<String> adjust(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long merchantId = requireMerchantId();
        if (!stockService.belongsToMerchant(id, merchantId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "库存记录不存在或无权限");
        }
        Integer qty = (Integer) body.get("qty");
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        stockService.adjust(id, qty, SecurityContext.getUserId(), remark);
        return R.ok("调整成功");
    }

    private Long requireMerchantId() {
        Long merchantId = SecurityContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未绑定商家身份");
        }
        return merchantId;
    }
}
