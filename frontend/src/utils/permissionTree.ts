import type { PermissionNode } from '@/api/types'

export type PermissionViewNode = PermissionNode & {
  displayOrder: string
  children?: PermissionViewNode[]
}

function comparePermissionNodes(left: PermissionNode, right: PermissionNode): number {
  const leftSortOrder = left.sortOrder ?? Number.MAX_SAFE_INTEGER
  const rightSortOrder = right.sortOrder ?? Number.MAX_SAFE_INTEGER

  return leftSortOrder - rightSortOrder || left.permId - right.permId
}

function decorateNodes(nodes: PermissionNode[], parentOrder = ''): PermissionViewNode[] {
  return [...nodes]
    .sort(comparePermissionNodes)
    .map((node, index) => {
      const displayOrder = parentOrder ? `${parentOrder}.${index + 1}` : String(index + 1)
      const children = node.children?.length ? decorateNodes(node.children, displayOrder) : []

      return {
        ...node,
        displayOrder,
        children,
      }
    })
}

export function buildPermissionTreeView(nodes: PermissionNode[]): PermissionViewNode[] {
  return decorateNodes(nodes)
}
