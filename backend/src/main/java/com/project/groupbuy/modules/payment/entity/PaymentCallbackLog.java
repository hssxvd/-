package com.project.groupbuy.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付回调日志表（约束文档 5.3 幂等）
 */
@Data
@TableName("payment_callback_log")
public class PaymentCallbackLog implements Serializable {

    private Long id;
    private String paymentNo;
    /** PAY / REFUND */
    private String callbackType;
    private String requestBody;
    private String responseBody;
    /** SUCCESS / FAIL / DUPLICATE */
    private String result;
    private String errorMsg;
    private LocalDateTime createTime;
}
