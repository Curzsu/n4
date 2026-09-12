package com.n4monitor.config;

import com.n4monitor.dashboard.controller.DashboardNavController;
import com.n4monitor.deployment.controller.DeploymentController;
import com.n4monitor.template.controller.TemplateController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeviceActionPermissionContractTest {
    @Test
    void protectsTemplateWriteEndpoints() {
        assertPermission(TemplateController.class, "createTemplate", "device:template:add");
        assertPermission(TemplateController.class, "updateTemplate", "device:template:edit");
        assertPermission(TemplateController.class, "deleteTemplate", "device:template:delete");
        assertPermission(TemplateController.class, "createPoint", "device:template:point:add");
        assertPermission(TemplateController.class, "updatePoint", "device:template:point:edit");
        assertPermission(TemplateController.class, "deletePoint", "device:template:point:delete");
        assertPermission(TemplateController.class, "importWorkbook", "device:template:import");
        assertPermission(TemplateController.class, "downloadImportTemplate", "device:template:import");
        assertPermission(TemplateController.class, "exportWorkbook", "device:template:export");
    }

    @Test
    void protectsDeploymentWriteEndpoints() {
        assertPermission(DeploymentController.class, "createDeployment", "device:deploy:add");
        assertPermission(DeploymentController.class, "updateDevice", "device:deploy:edit");
        assertPermission(DeploymentController.class, "moveDevice", "device:deploy:edit");
        assertPermission(DeploymentController.class, "updatePoint", "device:deploy:edit");
        assertPermission(DeploymentController.class, "deleteDeployment", "device:deploy:delete");
        assertPermission(DeploymentController.class, "importFiles", "device:deploy:import");
        assertPermission(DeploymentController.class, "downloadImportTemplate", "device:deploy:import");
        assertPermission(DeploymentController.class, "exportSnapshot", "device:deploy:export");

        assertPermission(DashboardNavController.class, "createNode", "device:deploy:group:add");
        assertPermission(DashboardNavController.class, "updateNode", "device:deploy:group:edit");
        assertPermission(DashboardNavController.class, "deleteNode", "device:deploy:group:delete");
        assertPermission(DashboardNavController.class, "mountDevices", "device:deploy:mount");
        assertPermission(DashboardNavController.class, "unmountDevice", "device:deploy:unmount");
    }

    private static void assertPermission(Class<?> controller, String methodName, String permissionCode) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing controller method: " + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, controller.getSimpleName() + "." + methodName);
        assertEquals("hasAuthority('" + permissionCode + "')", annotation.value());
    }
}
