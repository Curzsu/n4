package com.n4monitor.aspect;

import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.entity.SysOperationLog;
import com.n4monitor.service.SysOperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogAspectTest {

    @Test
    void recordsDeploymentPreviewWithADistinctOperationName() throws Throwable {
        SysOperationLogService logService = mock(SysOperationLogService.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{List.of(), true});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("com.n4monitor.deployment.controller.DeploymentController");
        when(signature.getName()).thenReturn("importFiles");

        new OperationLogAspect(logService).around(joinPoint, importOperation());

        var logCaptor = org.mockito.ArgumentCaptor.forClass(SysOperationLog.class);
        verify(logService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperation()).isEqualTo("Preview deployment files");
    }

    @Test
    void keepsDeploymentImportOperationNameForConfirmedImport() throws Throwable {
        SysOperationLogService logService = mock(SysOperationLogService.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{List.of(), false});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("com.n4monitor.deployment.controller.DeploymentController");
        when(signature.getName()).thenReturn("importFiles");

        new OperationLogAspect(logService).around(joinPoint, importOperation());

        var logCaptor = org.mockito.ArgumentCaptor.forClass(SysOperationLog.class);
        verify(logService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperation()).isEqualTo("Import deployment files");
    }

    private OperationLog importOperation() throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("importFiles", boolean.class);
        return method.getAnnotation(OperationLog.class);
    }

    private static final class Fixture {
        @OperationLog(
                module = "Deployment Management",
                operation = "Import deployment files",
                previewOperation = "Preview deployment files",
                businessType = BusinessType.IMPORT)
        private void importFiles(boolean dryRun) {
        }
    }
}
