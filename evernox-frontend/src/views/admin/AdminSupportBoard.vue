<template>
  <div class="admin-support-board">
    <div class="toolbar">
      <el-input v-model="newName" placeholder="画板名称" clearable style="width: 260px" @keyup.enter="create" />
      <el-button type="primary" :loading="creating" @click="create">创建画板</el-button>
    </div>

    <el-table :data="boards" border stripe>
      <el-table-column prop="name" label="画板名" min-width="160" />
      <el-table-column prop="width" label="宽" width="80" />
      <el-table-column prop="height" label="高" width="80" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.active === 1 ? 'success' : 'info'" size="small">
            {{ row.active === 1 ? '当前展示' : '未展示' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.active !== 1" link type="primary" @click="setActive(row as SupportBoardItem)">设为当前</el-button>
          <el-button link type="danger" @click="remove(row as SupportBoardItem)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSupportBoard,
  deleteSupportBoard,
  listSupportBoards,
  setSupportBoardActive,
} from '@/api/supportBoard'
import type { SupportBoardItem } from '@/api/supportBoard'

const boards = ref<SupportBoardItem[]>([])
const newName = ref('')
const creating = ref(false)

const load = async () => {
  const res = await listSupportBoards()
  boards.value = res.data ?? []
}

const create = async () => {
  if (!newName.value.trim()) {
    ElMessage.warning('请输入画板名称')
    return
  }
  creating.value = true
  try {
    await createSupportBoard(newName.value.trim())
    ElMessage.success('创建成功')
    newName.value = ''
    await load()
  } finally {
    creating.value = false
  }
}

const setActive = async (row: SupportBoardItem) => {
  await setSupportBoardActive(row.id)
  ElMessage.success('已设为当前画板')
  await load()
}

const remove = async (row: SupportBoardItem) => {
  await ElMessageBox.confirm(`确定删除画板「${row.name}」？其像素也会一并删除。`, '提示', { type: 'warning' })
  await deleteSupportBoard(row.id)
  ElMessage.success('删除成功')
  await load()
}

const formatTime = (v?: string) => {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<style scoped lang="scss">
.admin-support-board {
  .toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 14px;
  }
}
</style>
