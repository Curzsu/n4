package com.n4monitor.aspect;

import com.n4monitor.annotation.OperationLog;
import com.n4monitor.entity.SysOperationLog;
import com.n4monitor.security.LoginUser;
import com.n4monitor.security.SecurityUtils;
import com.n4monitor.service.SysOperationLogService;
import com.n4monitor.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.StringJoiner;
import java.util.regex.Pattern;

@Aspect
@Component
public class OperationLogAspect {
    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 2000;

    // Desensitization: match password keys in toString() output
    private static final Pattern SENSITIVE = Pattern.compile(
            "([a-zA-Z]*[Pp]assword=)[^,)\\]]*");

    private final SysOperationLogService logService;

    public OperationLogAspect(SysOperationLogService logService) {
        this.logService = logService;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        int status = 1;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            status = 0;
            errorMsg = ex.getMessage();
            throw ex;
        } finally {
            try {
                save(joinPoint, operationLog, status, errorMsg, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Failed to record operation audit log", e);
            }
        }
    }

    private void save(ProceedingJoinPoint joinPoint, OperationLog operationLog, int status, String errorMsg, long cost) {
        SysOperationLog entity = new SysOperationLog();
        entity.setModule(operationLog.module());
        Object[] args = joinPoint.getArgs();
        entity.setOperation(resolveOperation(operationLog, args));
        entity.setBusinessType(operationLog.businessType().getCode());
        entity.setStatus(status);
        entity.setErrorMsg(truncate(errorMsg, MAX_ERROR_LENGTH));
        entity.setCostTime(cost);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        entity.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());
        entity.setRequestParams(buildParams(args));

        LoginUser loginUser = SecurityUtils.getCurrentLoginUser();
        if (loginUser != null) {
            entity.setOperatorId(loginUser.getUserId());
            entity.setOperatorName(loginUser.getUsername());
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setRequestUri(request.getRequestURI());
            entity.setOperatorIp(IpUtils.getClientIp(request));
        }

        print(entity);
        logService.record(entity);
    }

    private String resolveOperation(OperationLog operationLog, Object[] args) {
        if (!operationLog.previewOperation().isBlank() && isDryRun(args)) {
            return operationLog.previewOperation();
        }
        return operationLog.operation();
    }

    private boolean isDryRun(Object[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        return Boolean.TRUE.equals(args[args.length - 1]);
    }

    private void print(SysOperationLog e) {
        if (e.getStatus() != null && e.getStatus() == 1) {
            log.info("AuditLog | Operator={} | {}/{} | {} {} | IP={} | Params={} | SUCCESS | Cost={}ms",
                    e.getOperatorName(), e.getModule(), e.getOperation(),
                    e.getRequestMethod(), e.getRequestUri(), e.getOperatorIp(), e.getRequestParams(), e.getCostTime());
        } else {
            log.warn("AuditLog | Operator={} | {}/{} | {} {} | IP={} | Params={} | FAILED | Cost={}ms | Reason={}",
                    e.getOperatorName(), e.getModule(), e.getOperation(),
                    e.getRequestMethod(), e.getRequestUri(), e.getOperatorIp(), e.getRequestParams(), e.getCostTime(), e.getErrorMsg());
        }
    }

    private String buildParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse || arg instanceof MultipartFile) {
                continue;
            }
            joiner.add(String.valueOf(arg));
        }
        String params = joiner.toString();
        if (params.isEmpty()) {
            return null;
        }
        params = SENSITIVE.matcher(params).replaceAll("$1******");
        return truncate(params, MAX_PARAM_LENGTH);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
