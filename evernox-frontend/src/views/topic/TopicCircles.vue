<template>
  <div class="topic-circles">
    <div class="page-header">
      <div>
        <h2>圈子</h2>
        <p>创建或关注你感兴趣的话题圈</p>
      </div>
      <el-button type="primary" @click="openCreate">创建圈子</el-button>
    </div>

    <div class="circles-layout">
      <div class="circles-main">
        <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索圈子名称"
        clearable
        style="width: 240px"
        @input="onKeywordInput"
        @clear="reload"
      />
      <el-checkbox v-model="mine" @change="reload">只看我关注的</el-checkbox>
    </div>

    <div v-loading="loading" class="circle-list">
      <div v-for="c in circles" :key="c.id" class="circle-card" @click="enter(c)">
        <div class="circle-avatar" :style="{ background: avatarColor(c.name) }">{{ (c.name || '?').slice(0, 1) }}</div>
        <div class="circle-body">
          <div class="circle-name">{{ c.name }}</div>
          <div class="circle-desc">{{ c.description || '暂无简介' }}</div>
          <div class="circle-meta">
            <span>{{ c.memberCount }} 成员</span>
            <span class="sep">·</span>
            <span>{{ c.postCount }} 帖子</span>
            <span class="sep">·</span>
            <span>创建者 {{ c.ownerName || '未知' }}</span>
          </div>
        </div>
        <div class="circle-actions" @click.stop>
          <el-button size="small" :type="c.followed ? 'default' : 'primary'" plain @click="toggleFollow(c)">
            {{ c.followed ? '已关注' : '关注' }}
          </el-button>
          <el-button v-if="canManage(c)" size="small" text @click="openEdit(c)">编辑</el-button>
          <el-button v-if="canManage(c)" size="small" text type="danger" @click="handleDelete(c)">删除</el-button>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !circles.length" description="还没有圈子，点右上角创建一个吧" class="circle-empty" />

    <div class="list-footer">
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="reload"
      />
    </div>

      </div>

      <aside class="circles-aside">
        <div class="aside-card">
          <div class="aside-title">圈子玩法</div>
          <ul class="aside-tips">
            <li><el-icon><CirclePlusFilled /></el-icon><span>创建圈子：建立你自己的话题圈</span></li>
            <li><el-icon><Star /></el-icon><span>关注圈子：第一时间看感兴趣的内容</span></li>
            <li><el-icon><EditPen /></el-icon><span>圈内发帖：在圈子里分享内容</span></li>
          </ul>
        </div>

        <div class="aside-card">
          <div class="aside-title">小贴士</div>
          <ul class="aside-tips">
            <li><el-icon><TrendCharts /></el-icon><span>热门圈子更活跃，帖子更容易被看到</span></li>
            <li><el-icon><Search /></el-icon><span>给圈子起个清晰的名字，方便别人搜索</span></li>
            <li><el-icon><Filter /></el-icon><span>关注后可用「只看我关注的」快速筛选</span></li>
          </ul>
        </div>
      </aside>
    </div>

    <el-dialog
      v-model="circleDialogVisible"
      :title="editingCircle ? '编辑圈子' : '创建圈子'"
      width="480px"
      append-to-body
    >
      <el-form label-width="64px">
        <el-form-item label="名称">
          <el-input v-model="circleForm.name" maxlength="50" show-word-limit placeholder="圈子名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="circleForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="圈子简介（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="circleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCircle">
          {{ editingCircle ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createTopicCircle,
  deleteTopicCircle,
  followCircle,
  getTopicCircles,
  unfollowCircle,
  updateTopicCircle,
} from '@/api/topic'
import type { TopicCircle } from '@/types/topic'

const router = useRouter()
const userStore = useUserStore()

const circles = ref<TopicCircle[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const mine = ref(false)
const loading = ref(false)

const circleDialogVisible = ref(false)
const editingCircle = ref<TopicCircle | null>(null)
const saving = ref(false)
const circleForm = reactive({ name: '', description: '' })

let keywordTimer: ReturnType<typeof setTimeout> | null = null

const load = async () => {
  loading.value = true
  try {
    const res = await getTopicCircles({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      mine: mine.value,
    })
    circles.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  load()
}

const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(reload, 300)
}

const enter = (c: TopicCircle) => {
  router.push(`/topic/circle/${c.id}`)
}

const canManage = (c: TopicCircle) => c.ownerId === userStore.userInfo?.id || userStore.isAdmin

const avatarColor = (name: string) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#9C27B0', '#00BCD4']
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return colors[hash % colors.length]
}

const toggleFollow = async (c: TopicCircle) => {
  if (c.followed) {
    await unfollowCircle(c.id)
    c.followed = false
    c.memberCount = Math.max(0, c.memberCount - 1)
  } else {
    await followCircle(c.id)
    c.followed = true
    c.memberCount += 1
  }
}

const openCreate = () => {
  editingCircle.value = null
  circleForm.name = ''
  circleForm.description = ''
  circleDialogVisible.value = true
}

const openEdit = (c: TopicCircle) => {
  editingCircle.value = c
  circleForm.name = c.name
  circleForm.description = c.description || ''
  circleDialogVisible.value = true
}

const submitCircle = async () => {
  if (!circleForm.name.trim()) {
    ElMessage.warning('请输入圈子名称')
    return
  }
  saving.value = true
  try {
    const data = { name: circleForm.name.trim(), description: circleForm.description.trim() || undefined }
    if (editingCircle.value) {
      await updateTopicCircle(editingCircle.value.id, data)
      ElMessage.success('保存成功')
    } else {
      await createTopicCircle(data)
      ElMessage.success('创建成功')
    }
    circleDialogVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (c: TopicCircle) => {
  await ElMessageBox.confirm(`确定删除圈子「${c.name}」吗？圈子内的帖子也会一并删除。`, '提示', {
    type: 'warning',
  })
  await deleteTopicCircle(c.id)
  ElMessage.success('删除成功')
  reload()
}

load()
</script>

<style scoped lang="scss">
.topic-circles {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 18px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;
  }

  .circles-layout {
    display: flex;
    gap: 20px;
    align-items: flex-start;
  }

  .circles-main {
    flex: 1;
    min-width: 0;
  }

  .circles-aside {
    width: 300px;
    flex-shrink: 0;
    position: sticky;
    top: 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .aside-card {
    padding: 16px;
    border-radius: 12px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
  }

  .aside-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--ev-text-primary);
    margin-bottom: 12px;
  }

  .aside-tips {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;

    li {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 13px;
      line-height: 1.6;
      color: var(--ev-text-regular);

      .el-icon {
        margin-top: 2px;
        color: var(--ev-primary);
        flex-shrink: 0;
      }
    }
  }

  @media (max-width: 900px) {
    .circles-layout {
      flex-direction: column;
    }

    .circles-aside {
      width: 100%;
      position: static;
    }
  }

  .circle-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .circle-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 14px 16px;
    border-radius: 12px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    cursor: pointer;
    transition: box-shadow 0.25s var(--ev-ease-out), border-color 0.25s var(--ev-ease-out);

    &:hover {
      border-color: var(--el-color-primary-light-5);
      box-shadow: var(--ev-shadow-sm);
    }

    .circle-avatar {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 600;
      color: #fff;
    }

    .circle-body {
      flex: 1;
      min-width: 0;
    }

    .circle-name {
      font-size: 15px;
      font-weight: 600;
      color: var(--ev-text-primary);
    }

    .circle-desc {
      margin: 4px 0;
      font-size: 13px;
      color: var(--ev-text-regular);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .circle-meta {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: var(--ev-text-muted);

      .sep {
        color: var(--ev-border-color);
      }
    }

    .circle-actions {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      gap: 2px;
    }
  }

  .circle-empty {
    margin-top: 20px;
  }

  .list-footer {
    margin-top: 18px;
    display: flex;
    justify-content: center;
  }
}
</style>
