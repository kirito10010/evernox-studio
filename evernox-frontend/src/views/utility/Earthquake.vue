<template>
  <div class="earthquake-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">地震信息</h2>
        <p class="page-sub">数据来源：中国地震台网速报（不定期更新）</p>
      </div>
      <el-button :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>
        <span>刷新</span>
      </el-button>
    </div>

    <div class="panel">
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="time" label="发震时间" min-width="170" />
        <el-table-column prop="location" label="震中位置" min-width="180" show-overflow-tooltip />
        <el-table-column label="震级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="magnitudeType(row.magnitude)" size="small" effect="dark">
              {{ row.magnitude }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="depth" label="深度(km)" width="100" align="center" />
        <el-table-column prop="longitude" label="经度" width="100" align="center" />
        <el-table-column prop="latitude" label="纬度" width="100" align="center" />
        <el-table-column prop="type" label="类型" width="110" align="center" />
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无地震数据" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getEarthquakeList } from '@/api/earthquake'
import type { EarthquakeItem } from '@/api/earthquake'

const loading = ref(false)
const list = ref<EarthquakeItem[]>([])

const load = async () => {
  loading.value = true
  try {
    const res = await getEarthquakeList()
    list.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

const magnitudeType = (m: string): 'info' | 'warning' | 'danger' => {
  const v = parseFloat(m)
  if (Number.isNaN(v) || v < 3) return 'info'
  if (v < 4) return 'warning'
  return 'danger'
}

onMounted(load)
</script>

<style scoped lang="scss">
.earthquake-page {
  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .page-title {
      margin: 0 0 4px;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    .page-sub {
      margin: 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }

  .panel {
    padding: 16px;
    border-radius: 14px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
  }
}
</style>
