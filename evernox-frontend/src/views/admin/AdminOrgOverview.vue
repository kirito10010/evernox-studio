<template>
  <div class="admin-org-overview">
    <div class="page-header">
      <h2>组织总览</h2>
      <p>
        所有账号创建的组织、创建者与该组织已加入的平台用户。审批仍遵循「谁创建、谁审批」，这里只做只读查看。
      </p>
    </div>

    <div class="summary">
      <span>组织 <strong>{{ list.length }}</strong> 个</span>
      <span>平台加入用户 <strong>{{ totalJoined }}</strong> 人</span>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand">
            <div class="expand-title">已加入的平台用户（{{ row.users.length }}）</div>
            <el-table v-if="row.users.length" :data="row.users" size="small" border>
              <el-table-column label="用户名" min-width="140">
                <template #default="{ row: user }">{{ user.username || '-' }}</template>
              </el-table-column>
              <el-table-column label="邮箱" min-width="200">
                <template #default="{ row: user }">{{ user.email || '-' }}</template>
              </el-table-column>
              <el-table-column label="申请时间" width="170">
                <template #default="{ row: user }">{{ formatDateTime(user.appliedAt) }}</template>
              </el-table-column>
              <el-table-column label="通过时间" width="170">
                <template #default="{ row: user }">{{ formatDateTime(user.reviewedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="还没有平台用户加入" :image-size="50" />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="组织" min-width="160" show-overflow-tooltip />
      <el-table-column label="创建者" min-width="200">
        <template #default="{ row }">
          <div class="owner">
            <span class="owner-name">{{ row.ownerUsername || '未知账号' }}</span>
            <el-tag size="small" :type="roleMeta(row.ownerRole).type" effect="plain">
              {{ roleMeta(row.ownerRole).label }}
            </el-tag>
          </div>
          <div class="owner-email">{{ row.ownerEmail || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="平台用户" width="110" align="right">
        <template #default="{ row }">{{ row.joinedUserCount }}</template>
      </el-table-column>
      <el-table-column label="游戏成员" width="110" align="right">
        <template #default="{ row }">{{ row.memberCount }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && list.length === 0" description="还没有任何组织" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { formatDateTime } from '@/utils/format'
import { getAdminOrgOverview, type AdminOrgOverview } from '@/api/adminOrgOverview'

const list = ref<AdminOrgOverview[]>([])
const loading = ref(false)

const totalJoined = computed(() => list.value.reduce((sum, org) => sum + org.joinedUserCount, 0))

const ROLE_META: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  admin: { label: '管理员', type: 'success' },
  super_member: { label: '超级会员', type: 'warning' },
  member: { label: '普通成员', type: 'info' },
}

const roleMeta = (role: string | null) =>
  (role && ROLE_META[role]) || { label: role || '未知', type: 'info' as const }

const load = async () => {
  loading.value = true
  try {
    const res = await getAdminOrgOverview()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.admin-org-overview {
  .page-header {
    h2 {
      margin: 0 0 4px;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p {
      margin: 0 0 12px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--ev-text-muted);
    }
  }

  .summary {
    display: flex;
    align-items: center;
    gap: 18px;
    margin-bottom: 12px;
    font-size: 13px;
    color: var(--ev-text-regular);

    strong {
      color: var(--ev-primary);
    }

    .el-button {
      margin-left: auto;
    }
  }

  .owner {
    display: flex;
    align-items: center;
    gap: 6px;

    .owner-name {
      font-weight: 600;
      color: var(--ev-text-primary);
    }
  }

  .owner-email {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  .expand {
    padding: 8px 16px;

    .expand-title {
      margin-bottom: 8px;
      font-size: 13px;
      font-weight: 600;
      color: var(--ev-text-primary);
    }
  }
}
</style>
