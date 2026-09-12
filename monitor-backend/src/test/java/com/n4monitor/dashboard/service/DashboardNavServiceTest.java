package com.n4monitor.dashboard.service;

import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
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

    private DashboardNavService navService;

    @BeforeEach
    void setUp() {
        navService = new DashboardNavService(navNodeMapper);
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
        when(navNodeMapper.findChildren(100L)).thenReturn(List.of());
        when(navNodeMapper.findChildren(10L)).thenReturn(List.of(subGroup));

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("into its own descendant");
    }

    @Test
    void batchMoveNodes_ExceedMaxLevelThrows() {
        NavNode root = createNode(1L, null, "A1_MVP", 1, 2); // max level is 2
        NavNode cabin = createNode(10L, 1L, "A1_MVP_A1", 2, null);
        NavNode subGroup = createNode(100L, 10L, "MVP_A1", 3, null);
        NavNode targetParent = createNode(2L, 1L, "A1_MVP_B1", 2, null);

        when(navNodeMapper.findByNodeId(2L)).thenReturn(targetParent);
        when(navNodeMapper.findByNodeId(1L)).thenReturn(root); // resolveMaxLevel traversal
        when(navNodeMapper.findByNodeId(10L)).thenReturn(cabin);
        when(navNodeMapper.findChildren(2L)).thenReturn(List.of());
        when(navNodeMapper.findChildren(10L)).thenReturn(List.of(subGroup));
        when(navNodeMapper.findChildren(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 2L))
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
        when(navNodeMapper.findChildren(2L)).thenReturn(List.of(existingChild));

        assertThatThrownBy(() -> navService.batchMoveNodes(List.of(10L), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already contains a node named 'A1_MVP_A1'");
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
