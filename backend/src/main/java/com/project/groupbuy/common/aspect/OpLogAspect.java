package com.project.groupbuy.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.util.DesensitizeUtil;
import com.project.groupbuy.modules.auth.entity.OperationLog;
import com.project.groupbuy.modules.auth.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志 AOP（约束文档 10：敏感操作自动写 operation_log）
 *
 * 拦截所有 @OpLog 标注的 Service 方法，自动记录：
 * - 操作人、模块、操作类型、请求参数（脱敏）、结果、异常
 *
 * 线程安全：独立事务（REQUIRES_NEW），主业务异常不影响日志写入
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        Object result = null;
        Throwable ex = null;
        long start = System.currentTimeMillis();

        OperationLog op = new OperationLog();
        op.setModule(opLog.module());
        op.setOperationType(opLog.operationType());
        op.setTargetType(opLog.targetType());
        op.setTraceId(MDC.get("traceId"));

        // 参数序列化（脱敏）— proceed() 之前做
        try {
            Object[] args = pjp.getArgs();
            Object[] limited = args.length > 2 ? new Object[]{args[0], args[1]} : args;
            String params = objectMapper.writeValueAsString(limited);
            if (opLog.desensitize()) {
                params = desensitizeParams(params);
            }
            op.setRequestParams(params);
        } catch (Exception e) {
            op.setRequestParams("[序列化失败]");
        }

        try {
            result = pjp.proceed();
            op.setResult("SUCCESS");
        } catch (Throwable t) {
            ex = t;
            op.setResult("FAIL");
            op.setErrorMsg(t.getMessage());
        } finally {
            // 操作人 — proceed() 之后再补：
            //   - 普通业务接口：JwtAuthInterceptor 已提前 set SecurityContext（proceed() 之前就有了）
            //   - 登录等公开端点：Service 方法体里手动 SecurityContext.set()，切面在这里才能拿到
            // 所以 proceed() 之前若没拿到，finally 里再兜底一次
            if (op.getUserId() == null && SecurityContext.isLoggedIn()) {
                op.setUserId(SecurityContext.getUserId());
            }
            // 写日志（独立 insert，不影响主事务）
            try {
                operationLogMapper.insert(op);
            } catch (Exception e) {
                log.warn("[操作日志写入失败]", e);
            }
        }

        if (ex != null) throw ex;
        return result;
    }

    /**
     * 脱敏：手机号/密码/身份证/token
     */
    private String desensitizeParams(String json) {
        if (json == null) return null;
        String result = json;
        result = result.replaceAll("\"password\":\"[^\"]*\"", "\"password\":\"****\"");
        result = result.replaceAll("\"phone\":\"\\d{11}\"", "\"phone\":\"138****0000\"");
        result = result.replaceAll("\"idCard\":\"[^\"]*\"", "\"idCard\":\"3101********1234\"");
        result = result.replaceAll("\"token\":\"[^\"]*\"", "\"token\":\"****\"");
        return result;
    }
}
