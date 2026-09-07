<template>
  <div class="admin-topic">
    <div class="page-header">
      <h2>话题集中营管理</h2>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 帖子管理 -->
      <el-tab-pane label="帖子管理" name="posts">
        <div class="filter-bar">
          <el-input
            v-model="postKeyword"
            placeholder="搜索标题"
            clearable
            style="width: 200px"
            @input="onPostKeywordInput"
            @clear="reloadPosts"
          />
          <el-input
            v-model="postUsername"
            placeholder="作者"
            clearable
            style="width: 140px"
            @input="onPostUsernameInput"
            @clear="reloadPosts"
          />
          <el-select v-model="postCircleId" placeholder="全部圈子" clearable style="width: 140px" @change="reloadPosts">
            <el-option v-for="c in circleOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-date-picker
            v-model="postDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="发布起"
            end-placeholder="止"
            class="filter-date"
            @change="reloadPosts"
          />
          <el-select v-model="postSortField" placeholder="默认排序" style="width: 140px" @change="reloadPosts">
            <el-option label="默认排序" value="" />
            <el-option label="发布时间" value="createdAt" />
            <el-option label="点赞数" value="likeCount" />
            <el-option label="评论数" value="commentCount" />
            <el-option label="收藏数" value="favoriteCount" />
          </el-select>
          <el-select v-model="postSortOrder" style="width: 100px" @change="reloadPosts">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
        </div>
        <el-table v-loading="postLoading" :data="postList" @selection-change="onPostSelectionChange">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="authorName" label="作者" width="120" />
          <el-table-column prop="circleName" label="圈子" width="140" />
          <el-table-column label="点赞/评论/收藏" width="150">
            <template #default="{ row }">{{ row.likeCount }} / {{ row.commentCount }} / {{ row.favoriteCount }}</template>
          </el-table-column>
          <el-table-column label="发布时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="handleDeletePost(row as TopicPost)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer">
          <el-button v-if="postSelected.length" type="danger" size="small" @click="handleBatchDeletePosts">
            批量删除（{{ postSelected.length }}）
          </el-button>
          <el-pagination
            v-model:current-page="postPage"
            v-model:page-size="postSize"
            :page-sizes="[10, 20, 50]"
            :total="postTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadPosts"
            @size-change="reloadPosts"
          />
        </div>
      </el-tab-pane>

      <!-- 评论管理 -->
      <el-tab-pane label="评论管理" name="comments">
        <div class="filter-bar">
          <el-input
            v-model="commentKeyword"
            placeholder="搜索评论内容"
            clearable
            style="width: 200px"
            @input="onCommentKeywordInput"
            @clear="reloadComments"
          />
          <el-input
            v-model="commentUsername"
            placeholder="评论者"
            clearable
            style="width: 140px"
            @input="onCommentUsernameInput"
            @clear="reloadComments"
          />
          <el-date-picker
            v-model="commentDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="评论起"
            end-placeholder="止"
            class="filter-date"
            @change="reloadComments"
          />
          <el-select v-model="commentSortOrder" style="width: 100px" @change="reloadComments">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
        </div>
        <el-table v-loading="commentLoading" :data="commentList" @selection-change="onCommentSelectionChange">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
          <el-table-column prop="authorName" label="评论者" width="120" />
          <el-table-column prop="postTitle" label="所属帖子" min-width="180" show-overflow-tooltip />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="handleDeleteComment(row as TopicComment)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer">
          <el-button v-if="commentSelected.length" type="danger" size="small" @click="handleBatchDeleteComments">
            批量删除（{{ commentSelected.length }}）
          </el-button>
          <el-pagination
            v-model:current-page="commentPage"
            v-model:page-size="commentSize"
            :page-sizes="[10, 20, 50]"
            :total="commentTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadComments"
            @size-change="reloadComments"
          />
        </div>
      </el-tab-pane>

      <!-- 圈子管理 -->
      <el-tab-pane label="圈子管理" name="circles">
        <div class="filter-bar">
          <el-input
            v-model="circleKeyword"
            placeholder="搜索圈子名称"
            clearable
            style="width: 200px"
            @input="onCircleKeywordInput"
            @clear="reloadCircles"
          />
          <el-input
            v-model="circleUsername"
            placeholder="创建者"
            clearable
            style="width: 140px"
            @input="onCircleUsernameInput"
            @clear="reloadCircles"
          />
          <el-date-picker
            v-model="circleDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="创建起"
            end-placeholder="止"
            class="filter-date"
            @change="reloadCircles"
          />
          <el-select v-model="circleSortField" placeholder="默认排序" style="width: 140px" @change="reloadCircles">
            <el-option label="默认排序" value="" />
            <el-option label="创建时间" value="createdAt" />
            <el-option label="帖子数" value="postCount" />
            <el-option label="成员数" value="memberCount" />
          </el-select>
          <el-select v-model="circleSortOrder" style="width: 100px" @change="reloadCircles">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
          <el-button type="primary" @click="openCreateCircle">新建圈子</el-button>
        </div>
        <el-table v-loading="circleLoading" :data="circleList" @selection-change="onCircleSelectionChange">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="ownerName" label="创建者" width="120" />
          <el-table-column prop="postCount" label="帖子数" width="90" />
          <el-table-column prop="memberCount" label="成员数" width="90" />
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEditCircle(row as TopicCircle)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDeleteCircle(row as TopicCircle)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer">
          <el-button v-if="circleSelected.length" type="danger" size="small" @click="handleBatchDeleteCircles">
            批量删除（{{ circleSelected.length }}）
          </el-button>
          <el-pagination
            v-model:current-page="circlePage"
            v-model:page-size="circleSize"
            :page-sizes="[10, 20, 50]"
            :total="circleTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadCircles"
            @size-change="reloadCircles"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 圈子新建/编辑 -->
    <el-dialog
      v-model="circleDialogVisible"
      :title="editingCircle ? '编辑圈子' : '新建圈子'"
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
        <el-button type="primary" :loading="circleSaving" @click="submitCircle">
          {{ editingCircle ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteAdminTopicCircles,
  batchDeleteAdminTopicComments,
  batchDeleteAdminTopicPosts,
  createAdminTopicCircle,
  deleteAdminTopicCircle,
  deleteAdminTopicComment,
  deleteAdminTopicPost,
  getAdminTopicCircles,
  getAdminTopicComments,
  getAdminTopicPosts,
  updateAdminTopicCircle,
} from '@/api/adminTopic'
import type { TopicCircle, TopicComment, TopicPost } from '@/types/topic'

const formatDateTime = (value: string): string => {
  if (!value) return '—'
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const activeTab = ref<'posts' | 'comments' | 'circles'>('posts')

// 帖子
const postList = ref<TopicPost[]>([])
const postTotal = ref(0)
const postPage = ref(1)
const postSize = ref(10)
const postKeyword = ref('')
const postUsername = ref('')
const postCircleId = ref<number | null>(null)
const postDateRange = ref<[string, string] | null>(null)
const postSortField = ref<'createdAt' | 'likeCount' | 'commentCount' | 'favoriteCount' | ''>('')
const postSortOrder = ref<'asc' | 'desc'>('desc')
const postLoading = ref(false)
const postSelected = ref<number[]>([])

// 评论
const commentList = ref<TopicComment[]>([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSize = ref(10)
const commentKeyword = ref('')
const commentUsername = ref('')
const commentDateRange = ref<[string, string] | null>(null)
const commentSortOrder = ref<'asc' | 'desc'>('desc')
const commentLoading = ref(false)
const commentSelected = ref<number[]>([])

// 圈子
const circleList = ref<TopicCircle[]>([])
const circleTotal = ref(0)
const circlePage = ref(1)
const circleSize = ref(10)
const circleKeyword = ref('')
const circleUsername = ref('')
const circleDateRange = ref<[string, string] | null>(null)
const circleSortField = ref<'createdAt' | 'postCount' | 'memberCount' | ''>('')
const circleSortOrder = ref<'asc' | 'desc'>('desc')
const circleLoading = ref(false)
const circleSelected = ref<number[]>([])
const circleOptions = ref<TopicCircle[]>([])

// 圈子新建/编辑
const circleDialogVisible = ref(false)
const editingCircle = ref<TopicCircle | null>(null)
const circleSaving = ref(false)
const circleForm = reactive({ name: '', description: '' })

let postTimer: ReturnType<typeof setTimeout> | null = null
let commentTimer: ReturnType<typeof setTimeout> | null = null
let circleTimer: ReturnType<typeof setTimeout> | null = null
let postUsernameTimer: ReturnType<typeof setTimeout> | null = null
let commentUsernameTimer: ReturnType<typeof setTimeout> | null = null
let circleUsernameTimer: ReturnType<typeof setTimeout> | null = null

// ============ 帖子 ============

const loadPosts = async () => {
  postLoading.value = true
  try {
    const res = await getAdminTopicPosts({
      page: postPage.value,
      size: postSize.value,
      keyword: postKeyword.value || undefined,
      username: postUsername.value.trim() || undefined,
      circleId: postCircleId.value,
      startDate: postDateRange.value?.[0],
      endDate: postDateRange.value?.[1],
      sortField: postSortField.value || undefined,
      sortOrder: postSortField.value ? postSortOrder.value : undefined,
    })
    postList.value = res.data?.records || []
    postTotal.value = res.data?.total || 0
  } finally {
    postLoading.value = false
  }
}

const reloadPosts = () => {
  postPage.value = 1
  loadPosts()
}

const onPostKeywordInput = () => {
  if (postTimer) clearTimeout(postTimer)
  postTimer = setTimeout(reloadPosts, 300)
}

const onPostUsernameInput = () => {
  if (postUsernameTimer) clearTimeout(postUsernameTimer)
  postUsernameTimer = setTimeout(reloadPosts, 300)
}

const onPostSelectionChange = (rows: TopicPost[]) => {
  postSelected.value = rows.map((r) => r.id)
}

const handleDeletePost = async (row: TopicPost) => {
  await ElMessageBox.confirm(`确定删除帖子「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteAdminTopicPost(row.id)
  ElMessage.success('删除成功')
  reloadPosts()
}

const handleBatchDeletePosts = async () => {
  await ElMessageBox.confirm(`确定删除选中的 ${postSelected.value.length} 条帖子吗？`, '批量删除', { type: 'warning' })
  await batchDeleteAdminTopicPosts(postSelected.value)
  ElMessage.success('删除成功')
  postSelected.value = []
  reloadPosts()
}

// ============ 评论 ============

const loadComments = async () => {
  commentLoading.value = true
  try {
    const res = await getAdminTopicComments({
      page: commentPage.value,
      size: commentSize.value,
      keyword: commentKeyword.value || undefined,
      username: commentUsername.value.trim() || undefined,
      startDate: commentDateRange.value?.[0],
      endDate: commentDateRange.value?.[1],
      sortField: undefined,
      sortOrder: commentSortOrder.value,
    })
    commentList.value = res.data?.records || []
    commentTotal.value = res.data?.total || 0
  } finally {
    commentLoading.value = false
  }
}

const reloadComments = () => {
  commentPage.value = 1
  loadComments()
}

const onCommentKeywordInput = () => {
  if (commentTimer) clearTimeout(commentTimer)
  commentTimer = setTimeout(reloadComments, 300)
}

const onCommentUsernameInput = () => {
  if (commentUsernameTimer) clearTimeout(commentUsernameTimer)
  commentUsernameTimer = setTimeout(reloadComments, 300)
}

const onCommentSelectionChange = (rows: TopicComment[]) => {
  commentSelected.value = rows.map((r) => r.id)
}

const handleDeleteComment = async (row: TopicComment) => {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await deleteAdminTopicComment(row.id)
  ElMessage.success('删除成功')
  reloadComments()
}

const handleBatchDeleteComments = async () => {
  await ElMessageBox.confirm(`确定删除选中的 ${commentSelected.value.length} 条评论吗？`, '批量删除', { type: 'warning' })
  await batchDeleteAdminTopicComments(commentSelected.value)
  ElMessage.success('删除成功')
  commentSelected.value = []
  reloadComments()
}

// ============ 圈子 ============

const loadCircles = async () => {
  circleLoading.value = true
  try {
    const res = await getAdminTopicCircles({
      page: circlePage.value,
      size: circleSize.value,
      keyword: circleKeyword.value || undefined,
      username: circleUsername.value.trim() || undefined,
      startDate: circleDateRange.value?.[0],
      endDate: circleDateRange.value?.[1],
      sortField: circleSortField.value || undefined,
      sortOrder: circleSortField.value ? circleSortOrder.value : undefined,
    })
    circleList.value = res.data?.records || []
    circleTotal.value = res.data?.total || 0
  } finally {
    circleLoading.value = false
  }
}

const reloadCircles = () => {
  circlePage.value = 1
  loadCircles()
}

const onCircleKeywordInput = () => {
  if (circleTimer) clearTimeout(circleTimer)
  circleTimer = setTimeout(reloadCircles, 300)
}

const onCircleUsernameInput = () => {
  if (circleUsernameTimer) clearTimeout(circleUsernameTimer)
  circleUsernameTimer = setTimeout(reloadCircles, 300)
}

const onCircleSelectionChange = (rows: TopicCircle[]) => {
  circleSelected.value = rows.map((r) => r.id)
}

const openCreateCircle = () => {
  editingCircle.value = null
  circleForm.name = ''
  circleForm.description = ''
  circleDialogVisible.value = true
}

const openEditCircle = (row: TopicCircle) => {
  editingCircle.value = row
  circleForm.name = row.name
  circleForm.description = row.description || ''
  circleDialogVisible.value = true
}

const submitCircle = async () => {
  if (!circleForm.name.trim()) {
    ElMessage.warning('请输入圈子名称')
    return
  }
  circleSaving.value = true
  try {
    const data = { name: circleForm.name.trim(), description: circleForm.description.trim() || undefined }
    if (editingCircle.value) {
      await updateAdminTopicCircle(editingCircle.value.id, data)
      ElMessage.success('保存成功')
    } else {
      await createAdminTopicCircle(data)
      ElMessage.success('创建成功')
    }
    circleDialogVisible.value = false
    reloadCircles()
  } finally {
    circleSaving.value = false
  }
}

const handleDeleteCircle = async (row: TopicCircle) => {
  await ElMessageBox.confirm(`确定删除圈子「${row.name}」吗？圈子内帖子会一并删除。`, '提示', { type: 'warning' })
  await deleteAdminTopicCircle(row.id)
  ElMessage.success('删除成功')
  reloadCircles()
}

const handleBatchDeleteCircles = async () => {
  await ElMessageBox.confirm(`确定删除选中的 ${circleSelected.value.length} 个圈子吗？`, '批量删除', { type: 'warning' })
  await batchDeleteAdminTopicCircles(circleSelected.value)
  ElMessage.success('删除成功')
  circleSelected.value = []
  reloadCircles()
}

const onTabChange = () => {
  if (activeTab.value === 'posts') loadPosts()
  else if (activeTab.value === 'comments') loadComments()
  else loadCircles()
}

const loadCircleOptions = async () => {
  try {
    const res = await getAdminTopicCircles({ page: 1, size: 100 })
    circleOptions.value = res.data?.records || []
  } catch {
    /* 请求层已提示 */
  }
}

loadPosts()
loadCircleOptions()
</script>

<style scoped lang="scss">
.admin-topic {
  .page-header {
    h2 {
      margin: 0 0 12px;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 14px;

    :deep(.filter-date) {
      flex: none !important;
      width: 250px !important;
      max-width: 250px !important;
    }
  }

  .table-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 14px;
  }
}
</style>
