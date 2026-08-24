<template>
  <div class="my-sites">
    <div class="page-header">
      <div class="header-text">
        <h2>我的分享</h2>
        <p>新建的分享默认私有；转为公开需管理员审批通过</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建分享
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="私有" name="0" />
      <el-tab-pane label="待审批" name="1" />
      <el-tab-pane label="已公开" name="2" />
      <el-tab-pane label="已驳回" name="3" />
    </el-tabs>

    <div v-loading="loading" class="card-grid">
      <SiteCard
        v-for="site in sites"
        :key="site.id"
        :site="site"
        :loader="decryptImage"
        show-status
      >
        <template #actions="{ site: row }">
          <el-button
            v-if="row.status === SiteStatus.PRIVATE || row.status === SiteStatus.REJECTED"
            size="small"
            @click="openEdit(row)"
          >编辑</el-button>
          <el-button
            v-if="row.status === SiteStatus.PRIVATE || row.status === SiteStatus.REJECTED"
            size="small"
            type="primary"
            @click="handleSubmit(row)"
          >申请公开</el-button>
          <el-button
            v-if="row.status === SiteStatus.PENDING"
            size="small"
            @click="handleWithdraw(row, '撤回申请')"
          >撤回申请</el-button>
          <el-button
            v-if="row.status === SiteStatus.PUBLIC"
            size="small"
            @click="handleWithdraw(row, '撤下并转为私有')"
          >撤下</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </SiteCard>
      <el-empty v-if="!loading && !sites.length" class="empty" description="还没有分享，先新建一个吧" />
    </div>

    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="loadSites"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑分享' : '新建分享'"
      width="520px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="网站名称" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：Vue 官方文档" />
        </el-form-item>
        <el-form-item label="网站链接" prop="url">
          <el-input v-model="form.url" maxlength="500" placeholder="https://example.com" />
        </el-form-item>
        <el-form-item label="详情介绍" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="这个网站是做什么的、适合谁用"
          />
        </el-form-item>
        <el-form-item label="网站图标">
          <div class="cover-field">
            <div v-if="pendingPreview" class="cover-preview">
              <img :src="pendingPreview" alt="网站图标预览" />
            </div>
            <div v-else-if="form.coverImageId" class="cover-preview">
              <LazyImage :image-id="form.coverImageId" :loader="decryptImage" ratio="1 / 1" />
            </div>
            <div class="cover-ops">
              <el-button size="small" @click="coverCropperVisible = true">
                {{ hasCover ? '更换网站图标' : '上传网站图标' }}
              </el-button>
              <el-button
                v-if="hasCover"
                size="small"
                text
                type="danger"
                @click="clearCover"
              >移除</el-button>
              <span class="cover-tip">选填，1:1 方图，选图后可缩放裁剪</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 与相册封面同一套裁剪交互，只是比例固定 1:1 -->
    <AlbumCoverCropper
      v-model="coverCropperVisible"
      :aspect="1"
      :output-width="COVER_OUT_SIZE"
      @cropped="handleCoverCropped"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import LazyImage from '@/components/LazyImage.vue'
import SiteCard from '@/components/SiteCard.vue'
import AlbumCoverCropper from '@/components/AlbumCoverCropper.vue'
import { createSite, deleteSite, getMySites, submitSite, updateSite, withdrawSite } from '@/api/site'
import { uploadImage } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { SiteStatus } from '@/types/site'
import type { SiteLink, SiteLinkPayload } from '@/types/site'

const { decryptImage, clearCache } = useImageDecrypt()

const sites = ref<SiteLink[]>([])
const loading = ref(false)
const activeTab = ref('all')
const currentPage = ref(1)
const pageSize = 12
const total = ref(0)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
/** 本次选中但尚未上传的图标文件，保存时才真正上传 */
const pendingFile = ref<File | null>(null)
/** 待上传文件的本地预览地址 */
const pendingPreview = ref<string | null>(null)
const coverCropperVisible = ref(false)
/** 网站图标输出边长（1:1 方图） */
const COVER_OUT_SIZE = 512
const formRef = ref<FormInstance>()
const form = reactive<{
  title: string
  url: string
  description: string
  coverImageId: number | null
}>({ title: '', url: '', description: '', coverImageId: null })

const hasCover = computed(() => !!pendingPreview.value || form.coverImageId !== null)

/** 释放本地预览地址，否则 objectURL 会一直占着内存 */
const revokePreview = () => {
  if (pendingPreview.value) {
    URL.revokeObjectURL(pendingPreview.value)
    pendingPreview.value = null
  }
}

const rules: FormRules = {
  title: [{ required: true, message: '请填写网站名称', trigger: 'blur' }],
  url: [
    { required: true, message: '请填写网站链接', trigger: 'blur' },
    {
      pattern: /^https?:\/\/\S+$/,
      message: '链接必须以 http:// 或 https:// 开头',
      trigger: 'blur',
    },
  ],
}

const loadSites = async () => {
  loading.value = true
  try {
    const res = await getMySites({
      page: currentPage.value,
      size: pageSize,
      status: activeTab.value === 'all' ? undefined : Number(activeTab.value),
    })
    sites.value = res.data?.records || []
    total.value = res.data?.total || 0
    // 删空末页时回退，否则会停在空页
    if (!sites.value.length && currentPage.value > 1) {
      currentPage.value -= 1
      await loadSites()
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

const onTabChange = () => {
  currentPage.value = 1
  void loadSites()
}

const openCreate = () => {
  // 打开即净态：@closed 是关闭动画结束后的异步回调，指望它清理会让上一次
  // 残留的 pendingFile 活到这一次，导致新分享套用上一张封面
  resetForm()
  dialogVisible.value = true
}

const openEdit = (site: SiteLink) => {
  editingId.value = site.id
  form.title = site.title
  form.url = site.url
  form.description = site.description || ''
  form.coverImageId = site.coverImageId
  revokePreview()
  pendingFile.value = null
  dialogVisible.value = true
}

const resetForm = () => {
  editingId.value = null
  form.title = ''
  form.url = ''
  form.description = ''
  form.coverImageId = null
  revokePreview()
  pendingFile.value = null
  formRef.value?.clearValidate()
}

/** 只做本地暂存与预览，真正上传推迟到点击保存时 */
const handleCoverCropped = (file: File) => {
  revokePreview()
  pendingFile.value = file
  pendingPreview.value = URL.createObjectURL(file)
}

const clearCover = () => {
  revokePreview()
  pendingFile.value = null
  form.coverImageId = null
}

const handleSave = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    // 图标选填：仅当本次选了新文件时才上传，先私密落库，审批通过后由后端联动放开
    let coverImageId = form.coverImageId
    if (pendingFile.value) {
      const file = pendingFile.value
      const res = await uploadImage(file, {
        originalName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        width: COVER_OUT_SIZE,
        height: COVER_OUT_SIZE,
        visibility: 0,
        purpose: 2,
      })
      if (!res.data?.id) {
        ElMessage.error('网站图标上传失败，请重试')
        return
      }
      coverImageId = res.data.id
      // 立刻把待上传状态换成已落库的 id：后续 createSite/updateSite 若失败，
      // 弹窗仍开着，此时重试应复用这张已上传的图，而不是再传一遍
      form.coverImageId = coverImageId
      revokePreview()
      pendingFile.value = null
    }

    const payload: SiteLinkPayload = {
      title: form.title.trim(),
      url: form.url.trim(),
      description: form.description.trim() || null,
      coverImageId,
    }
    if (editingId.value) await updateSite(editingId.value, payload)
    else await createSite(payload)
    dialogVisible.value = false
    await loadSites()
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}

const handleSubmit = async (site: SiteLink) => {
  try {
    await ElMessageBox.confirm('提交后进入管理员审批队列，审批期间不可编辑。', '申请公开', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await submitSite(site.id)
    ElMessage.success('已提交审批')
    await loadSites()
  } catch {
    /* 请求层已提示 */
  }
}

const handleWithdraw = async (site: SiteLink, actionText: string) => {
  try {
    await ElMessageBox.confirm(`确定${actionText}？站点将变为仅自己可见。`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await withdrawSite(site.id)
    ElMessage.success('已转为私有')
    await loadSites()
  } catch {
    /* 请求层已提示 */
  }
}

const handleDelete = async (site: SiteLink) => {
  try {
    await ElMessageBox.confirm(`确定删除「${site.title}」？此操作不可撤销。`, '删除分享', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteSite(site.id)
    ElMessage.success('已删除')
    await loadSites()
  } catch {
    /* 请求层已提示 */
  }
}

onMounted(() => {
  void loadSites()
})

onUnmounted(() => {
  revokePreview()
  clearCache()
})
</script>

<style scoped lang="scss">
.my-sites {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  h2 {
    margin: 0;
    font-size: 22px;
  }

  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--ev-text-muted, #90a4bb);
  }
}

.card-grid {
  display: grid;
  /* 比公开页宽一点：图块下方的操作条要放编辑/申请公开/删除 */
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 14px;
  min-height: 200px;
}

.empty {
  grid-column: 1 / -1;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
}

.cover-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
}

.cover-preview {
  width: 160px;
  border: 1px solid var(--ev-border, #e6ecf3);
  border-radius: 8px;
  overflow: hidden;

  img {
    display: block;
    width: 100%;
    aspect-ratio: 1 / 1;
    object-fit: cover;
  }
}

.cover-ops {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cover-tip {
  font-size: 12px;
  line-height: 1.4;
  color: var(--ev-text-muted, #90a4bb);
}
</style>


