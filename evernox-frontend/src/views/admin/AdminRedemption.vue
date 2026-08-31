<template>
  <div class="admin-redemption">
    <div class="toolbar">
      <span class="toolbar-label">时长：</span>
      <el-radio-group v-model="days">
        <el-radio-button :value="7">一周（7天）</el-radio-button>
        <el-radio-button :value="30">一个月（30天）</el-radio-button>
      </el-radio-group>
      <span class="toolbar-label">数量：</span>
      <el-input-number v-model="count" :min="1" :max="100" :precision="0" />
      <el-button type="primary" :loading="generating" @click="doGenerate">生成卡密</el-button>
    </div>

    <el-table :data="codes" border stripe v-loading="loading">
      <el-table-column prop="code" label="卡密" min-width="200">
        <template #default="{ row }">
          <span class="code">{{ row.code }}</span>
          <el-button link type="primary" size="small" @click="copyCode(row as RedemptionCode)">复制</el-button>
        </template>
      </el-table-column>
      <el-table-column label="时长" width="120">
        <template #default="{ row }">{{ row.days }} 天</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '已使用' : '未使用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="使用账户" min-width="120">
        <template #default="{ row }">{{ row.username || '-' }}</template>
      </el-table-column>
      <el-table-column label="使用时间" min-width="160">
        <template #default="{ row }">{{ fmtTime(row.usedAt) }}</template>
      </el-table-column>
      <el-table-column label="生成时间" min-width="160">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="generateVisible" title="生成的卡密" width="480px">
      <div class="generated-list">
        <div v-for="c in generatedCodes" :key="c.id" class="generated-item">
          <span class="code">{{ c.code }}</span>
          <span class="meta">{{ c.days }} 天</span>
          <el-button link type="primary" size="small" @click="copyText(c.code)">复制</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="copyAll">复制全部</el-button>
        <el-button @click="generateVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { generateRedemptionCodes, getRedemptionCodes } from '@/api/admin'
import type { RedemptionCode } from '@/types/user'

const days = ref(7)
const count = ref(1)
const generating = ref(false)
const codes = ref<RedemptionCode[]>([])
const loading = ref(false)
const generatedCodes = ref<RedemptionCode[]>([])
const generateVisible = ref(false)

const fmtTime = (v?: string | null): string => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-')

const loadCodes = async () => {
  loading.value = true
  try {
    const res = await getRedemptionCodes()
    codes.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

const doGenerate = async () => {
  generating.value = true
  try {
    const res = await generateRedemptionCodes({ days: days.value, count: count.value })
    generatedCodes.value = res.data ?? []
    generateVisible.value = true
    await loadCodes()
  } finally {
    generating.value = false
  }
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const copyCode = (row: RedemptionCode) => copyText(row.code)

const copyAll = () => {
  const text = generatedCodes.value.map((c) => c.code).join('\n')
  copyText(text)
}

onMounted(loadCodes)
</script>

<style scoped lang="scss">
.admin-redemption {
  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
    flex-wrap: wrap;

    .toolbar-label {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }

  .code {
    font-family: monospace;
    letter-spacing: 1px;
    color: var(--ev-text-primary);
    margin-right: 6px;
  }

  .generated-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-height: 400px;
    overflow-y: auto;

    .generated-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      background: rgba(47, 124, 246, 0.05);
      border-radius: 8px;

      .code {
        flex: 1;
      }

      .meta {
        font-size: 12px;
        color: var(--ev-text-muted);
      }
    }
  }
}
</style>
