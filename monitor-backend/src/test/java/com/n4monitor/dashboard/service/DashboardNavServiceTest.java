package com.n4monitor.dashboard.service;

import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.deployment.mapper.PointDeploymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardNavServiceTest {

    @Mock
    private NavNodeMapper navNodeMapper;
    @Mock
    private DeviceDeploymentMapper deviceDeploymentMapper;
    @Mock
    private PointDeploymentMapper pointDeploymentMapper;

    private DashboardNavService navService;

    @BeforeEach
    void setUp() {
        navService = new DashboardNavService(navNodeMapper, deviceDeploymentMapper, pointDeploymentMapper);
    }

    @Test
    void batchMoveNodes_SuccessWithSubtree() {
        NavNode root1 = createNode(1L, null, "A1_MVP", 1, 3);
        NavNode root2 = createNode(2L, null, "A2_MVP", 1, 3);

        NavNode cabin1 = createNode(10L, 1L, "A1_MVP_A1", 2, null);
        NavNode subGroup1 = createNode(100L, 10L, "MVP_A1", 3, null);

        NavNode cabin2 = createNode(11L, 1L, "A1_MVP_B1", 2, null);

        when(navNodeMapper.findByNodeId(2L)).thenReturn(root2);
        when(navNodeMapper.findByNodeId(10L)).thenReturn(cabin1);
        when(navNodeMapper.findByNodeId(11L)).thenReturn(cabin2);

        // Children setup
        when(navNodeMapper.findChildren(2L)).thenReturn(List.of());
        when(navNodeMapper.findChildren(10L)).thenReturn(List.of(subGroup1));
        when(navNodeMapper.findChildren(100L)).thenReturn(List.of());
        when(navNodeMapper.findChildren(11L)).thenReturn(List.of());

        int moved = navService.batchMoveNodes(List.of(10L, 11L), 2L);

        assertThat(moved).isEqualTo(2);
        assertThat(cabin1.getParentId()).isEqualTo(2L);
        assertThat(cabin1.getNodeLevel()).isEqualTo(2);
        assertThat(cabin2.getParentId()).isEqualTo(2L);
        assertThat(cabin2.getNodeLevel()).isEqualTo(2);
        verify(navNodeMapper).update(cabin1);
        verify(navNodeMapper).update(cabin2);
    }

    @Test
    void batchMoveNodes_SelfMoveThrows() {
        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot move a node into itself");
    }

    @Test
    void batchMoveNodes_DescendantMoveThrows() {
        NavNode root = createNode(1L, null, "A1_MVP", 1, 3);
        NavNode cabin = createNode(10L, 1L, "A1_MVP_A1", 2, null);
        NavNode subGroup = createNode(100L, 10L, "MVP_A1", 3, null);

        when(navNodeMapper.findByNodeId(100L)).thenReturn(subGroup);
        when(navNodeMapper.findByNodeId(10L)).thenReturn(cabin);
        when(navNodeMapper.findChildren(10L)).thenReturn(List.of(subGroup));

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("into its own descendant");
    }

    @Test
    void batchMoveNodes_CabinToNonRootThrows() {
        NavNode cabin = createNode(10L, 1L, "A1_MVP_A1", 2, null);
        NavNode targetCabin = createNode(2L, 1L, "A1_MVP_B1", 2, null);

        when(navNodeMapper.findByNodeId(2L)).thenReturn(targetCabin);
        when(navNodeMapper.findByNodeId(10L)).thenReturn(cabin);

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cabins can only be moved to a building or system root node (level 1)");
    }

    @Test
    void batchMoveNodes_ExceedMaxLevelThrows() {
        NavNode root = createNode(1L, null, "A1_MVP", 1, 2); // max level is 2
        NavNode collector = createNode(100L, 10L, "PTU_A1", 3, null);
        NavNode targetParent = createNode(2L, 1L, "A1_MVP_B1", 2, null);

        when(navNodeMapper.findByNodeId(2L)).thenReturn(targetParent);
        when(navNodeMapper.findByNodeId(1L)).thenReturn(root); // resolveMaxLevel traversal
        when(navNodeMapper.findByNodeId(100L)).thenReturn(collector);
        when(navNodeMapper.findChildren(2L)).thenReturn(List.of());
        when(navNodeMapper.findChildren(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(100L), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would exceed maximum directory levels");
    }

    @Test
    void batchMoveNodes_DuplicateNameThrows() {
        NavNode root2 = createNode(2L, null, "A2_MVP", 1, 3);
        NavNode existingChild = createNode(20L, 2L, "A1_MVP_A1", 2, null);
        NavNode cabin = createNode(10L, 1L, "A1_MVP_A1", 2, null);

        when(navNodeMapper.findByNodeId(2L)).thenReturn(root2);
        when(navNodeMapper.findByNodeId(10L)).thenReturn(cabin);
        when(navNodeMapper.findChildren(10L)).thenReturn(List.of());
        when(navNodeMapper.findChildren(2L)).thenReturn(List.of(existingChild));

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already contains a node named 'A1_MVP_A1'");
    }

    @Test
    void batchMoveNodes_MergeIntoLeafContainer_Success() {
        // Target is MVP_B1 (nodeId: 12, level 3, maxLevel 3)
        NavNode root = createNode(1L, null, "A1_MVP", 1, 3);
        NavNode folder = createNode(5L, 1L, "A1_MVP_B1", 2, null);
        NavNode leafTarget = createNode(12L, 5L, "MVP_B1", 3, null);

        // Source is mvp_D1 (nodeId: 50, level 3)
        NavNode source = createNode(50L, 1L, "mvp_D1", 3, null);

        when(navNodeMapper.findByNodeId(12L)).thenReturn(leafTarget);
        when(navNodeMapper.findByNodeId(5L)).thenReturn(folder);
        when(navNodeMapper.findByNodeId(1L)).thenReturn(root);
        when(navNodeMapper.findByNodeId(50L)).thenReturn(source);

        // Leaf container indicators: level >= maxAllowedLevel (3 >= 3) and countDevicesByNodeId > 0
        when(navNodeMapper.countDevicesByNodeId(12L)).thenReturn(43);
        when(navNodeMapper.findChildren(50L)).thenReturn(List.of());

        // Existing device in target to resolve system name
        DeviceDeployment existingTargetDev = new DeviceDeployment();
        existingTargetDev.setDeviceDeployId(90L);
        existingTargetDev.setSystemName("A1_MVP_B1/MVP_B1");
        when(deviceDeploymentMapper.findByNavNodeId(12L)).thenReturn(List.of(existingTargetDev));
        when(deviceDeploymentMapper.findMaxDeviceIdByNavNodeId(12L)).thenReturn(90L);
        when(deviceDeploymentMapper.findMaxDeviceId()).thenReturn(95L);

        // Devices inside mvp_D1 (new devices with IDs 91 and 92)
        DeviceDeployment dev1 = new DeviceDeployment();
        dev1.setDeviceDeployId(91L);
        dev1.setNavNodeId(50L);
        dev1.setDeviceDeployName("mvp_D1_Dev1");
        dev1.setN4Address("slot:/Drivers/ModbusTcpNetwork/mvp_D1/points/MqttNetwork/mvp_D1_Dev1");

        DeviceDeployment dev2 = new DeviceDeployment();
        dev2.setDeviceDeployId(92L);
        dev2.setNavNodeId(50L);
        dev2.setDeviceDeployName("mvp_D1_Dev2");
        dev2.setN4Address("slot:/Drivers/ModbusTcpNetwork/mvp_D1/points/MqttNetwork/mvp_D1_Dev2");

        when(deviceDeploymentMapper.findByNavNodeId(50L)).thenReturn(List.of(dev1, dev2));

        int moved = navService.batchMoveNodes(List.of(50L), 12L);

        assertThat(moved).isEqualTo(1);
        assertThat(dev1.getNavNodeId()).isEqualTo(12L);
        assertThat(dev1.getSystemName()).isEqualTo("A1_MVP_B1/MVP_B1");
        assertThat(dev1.getN4Address()).isEqualTo("slot:/Drivers/ModbusTcpNetwork/MVP_B1/points/MqttNetwork/mvp_D1_Dev1");
        assertThat(dev2.getNavNodeId()).isEqualTo(12L);
        assertThat(dev2.getSystemName()).isEqualTo("A1_MVP_B1/MVP_B1");
        assertThat(dev2.getN4Address()).isEqualTo("slot:/Drivers/ModbusTcpNetwork/MVP_B1/points/MqttNetwork/mvp_D1_Dev2");

        verify(deviceDeploymentMapper).update(dev1);
        verify(deviceDeploymentMapper).update(dev2);
        verify(navNodeMapper).unmountAllByNode(50L);
        verify(navNodeMapper).logicalDelete(50L);
    }

    @Test
    void batchMoveNodes_MergeIntoLeafContainer_RenumbersOlderDevices() {
        NavNode root = createNode(1L, null, "A1_MVP", 1, 3);
        NavNode leafTarget = createNode(12L, 1L, "MVP_B1", 3, null);
        NavNode source = createNode(50L, 1L, "mvp_D1", 3, null);

        when(navNodeMapper.findByNodeId(12L)).thenReturn(leafTarget);
        when(navNodeMapper.findByNodeId(1L)).thenReturn(root);
        when(navNodeMapper.findByNodeId(50L)).thenReturn(source);
        when(navNodeMapper.countDevicesByNodeId(12L)).thenReturn(43);
        when(navNodeMapper.findChildren(50L)).thenReturn(List.of());

        when(deviceDeploymentMapper.findByNavNodeId(12L)).thenReturn(List.of());
        // Existing max target device ID is 90, but source device has older ID 20 <= 90
        when(deviceDeploymentMapper.findMaxDeviceIdByNavNodeId(12L)).thenReturn(90L);
        when(deviceDeploymentMapper.findMaxDeviceId()).thenReturn(95L);

        DeviceDeployment oldDev = new DeviceDeployment();
        oldDev.setDeviceDeployId(20L);
        oldDev.setNavNodeId(50L);
        when(deviceDeploymentMapper.findByNavNodeId(50L)).thenReturn(List.of(oldDev));

        int moved = navService.batchMoveNodes(List.of(50L), 12L);

        assertThat(moved).isEqualTo(1);
        // ID should be renumbered to globalMax (95) + 1 = 96
        assertThat(oldDev.getDeviceDeployId()).isEqualTo(96L);
        verify(pointDeploymentMapper).updateDeviceDeployId(20L, 96L);
        verify(deviceDeploymentMapper).updateDeviceId(20L, 96L);
        verify(deviceDeploymentMapper).update(oldDev);
    }

    private NavNode createNode(Long id, Long parentId, String name, Integer level, Integer maxLevel) {
        NavNode node = new NavNode();
        node.setNodeId(id);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeLevel(level);
        node.setMaxLevel(maxLevel);
        node.setIsDeleted(false);
        return node;
    }
}
