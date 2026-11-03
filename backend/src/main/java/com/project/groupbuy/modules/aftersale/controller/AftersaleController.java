package com.project.groupbuy.modules.aftersale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.aftersale.dto.AftersaleApplyRequest;
import com.project.groupbuy.modules.aftersale.dto.AftersaleAuditRequest;
import com.project.groupbuy.modules.aftersale.dto.AftersaleQueryRequest;
import com.project.groupbuy.modules.aftersale.service.AftersaleService;
import com.project.groupbuy.modules.aftersale.vo.AftersaleDetailVO;
import com.project.groupbuy.modules.aftersale.vo.AftersaleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 售后控制器（约束文档 5.5 售后状态机 + 11 分页与统计）
 *
 * 端点：
 *   POST /aftersales                    — 消费者申请售后（body）
 *   GET  /aftersales/mine               — 消费者我的售后（分页）
 *   GET  /aftersales/merchant           — 商家我的售后（分页，数据隔离）
 *   GET  /aftersales/merchant/stats     — 商家售后统计卡
 *   GET  /aftersales/{id:\d+}           — 售后详情（按角色校验归属）
 *   POST /aftersales/{id:\d+}/audit     — 商家审核（body）
 *
 * 注：路径变量一律加 {@code \d+} 正则约束，防止 /mine、/merchant 被贪婪匹配（历史踩坑）。
 */
@Slf4j
@RestController
@RequestMapping("/aftersales")
@RequiredArgsConstructor
public class AftersaleController {

    private final AftersaleService aftersaleService;

    /**
     * POST /aftersales — 消费者申请售后
     */
    @PostMapping
    public R<AftersaleDetailVO> apply(@Valid @RequestBody AftersaleApplyRequest req) {
        return R.ok(aftersaleService.apply(req));
    }

    /**
     * GET /aftersales/mine — 消费者我的售后（分页 + 状态/类型/关键词/日期筛选）
     */
    @GetMapping("/mine")
    public R<IPage<AftersaleVO>> mine(@Valid AftersaleQueryRequest req) {
        return R.ok(aftersaleService.pageMine(req));
    }

    /**
     * GET /aftersales/merchant — 商家我的售后（分页，数据隔离）
     */
    @GetMapping("/merchant")
    public R<IPage<AftersaleVO>> merchant(@Valid AftersaleQueryRequest req) {
        return R.ok(aftersaleService.pageByMerchant(req));
    }

    /**
     * GET /aftersales/merchant/stats — 商家售后统计卡
     */
    @GetMapping("/merchant/stats")
    public R<Map<String, Object>> merchantStats() {
        return R.ok(aftersaleService.statsByMerchant());
    }

    /**
     * GET /aftersales/{id:\d+} — 售后详情（含处理轨迹时间线）
     */
    @GetMapping("/{id:\\d+}")
    public R<AftersaleDetailVO> detail(@PathVariable Long id) {
        return R.ok(aftersaleService.detail(id));
    }

    /**
     * POST /aftersales/{id:\d+}/audit — 商家审核（通过后自动退款 + 退货回库）
     */
    @PostMapping("/{id:\\d+}/audit")
    public R<AftersaleDetailVO> audit(@PathVariable Long id,
                                      @Valid @RequestBody AftersaleAuditRequest req) {
        return R.ok(aftersaleService.audit(id, req.getApprove(), req.getRemark()));
    }
}
