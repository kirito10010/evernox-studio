<template>
  <div class="ninja-guide">
    <div class="page-header">
      <div>
        <h2>忍者图鉴</h2>
        <p>火影忍者OL 忍者介绍 · 悬停查看技能</p>
      </div>
      <el-button v-if="userStore.isAdmin" :loading="refreshing" @click="onRefresh">刷新忍者数据</el-button>
    </div>

    <div class="guide-layout">
      <div class="guide-main">
        <div class="filter-bar">
          <el-input
            v-model="keyword"
            placeholder="搜索忍者名称 / 昵称"
            clearable
            class="filter-search"
            @input="onSearch"
            @clear="onSearch"
          />
          <el-select v-model="attr" placeholder="全部属性" clearable class="filter-attr" @change="reload">
            <el-option v-for="a in ATTRIBUTES" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="hurtType" placeholder="伤害类型" clearable class="filter-attr" @change="reload">
            <el-option v-for="a in HURT_TYPES" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="chaseStatus" placeholder="追打条件" clearable class="filter-attr" @change="reload">
            <el-option v-for="a in CHASE_STATUSES" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="hurtStatus" placeholder="造成状态" clearable class="filter-attr" @change="reload">
            <el-option v-for="a in HURT_STATUSES" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="rare" placeholder="稀有度" clearable class="filter-attr" @change="reload">
            <el-option v-for="a in RARITIES" :key="a" :label="a" :value="a" />
          </el-select>
        </div>
        <div v-loading="loading" class="ninja-grid">
          <div
            v-for="n in list"
            :key="n.id"
            class="ninja-card"
            @mouseenter="onCardEnter($event)"
            @mousemove="onCardMove($event)"
            @mouseleave="onCardLeave"
          >
            <div class="card-avatar">
              <img :src="n.avatarUrl3 || n.avatarUrl || undefined" :alt="n.nickname || n.name || ''" loading="lazy" />
            </div>
            <div class="card-name">{{ n.nickname || n.name }}</div>
            <div class="card-tags">
              <span v-if="n.attr" class="tag attr">{{ n.attr }}</span>
              <span v-if="n.star" class="tag star">{{ n.star }}星</span>
            </div>

            <div class="skill-popover">
              <div class="pop-header">
                <span class="pop-name">{{ n.name || n.nickname }}</span>
              </div>
              <div v-if="n.skills && n.skills.length" class="skill-list">
                <div v-for="(s, i) in n.skills" :key="i" class="skill-item">
                  <img class="skill-icon" :src="s.iconUrl || undefined" :alt="s.title || ''" loading="lazy" />
                  <div class="skill-body">
                    <div class="skill-head">
                      <span v-if="s.type" class="skill-type">{{ s.type }}</span>
                      <span class="skill-title">{{ s.title }}</span>
                    </div>
                    <div class="skill-desc" v-html="skillDescHtml(s)"></div>
                  </div>
                </div>
              </div>
              <div v-else class="pop-empty">暂无技能数据</div>
              <div v-if="n.getWay" class="pop-getway">获得方式：{{ n.getWay }}</div>
            </div>
          </div>

          <el-empty
            v-if="!loading && !list.length"
            class="grid-empty"
            description="暂无忍者数据，点右上角刷新获取"
          />
        </div>

        <div class="list-footer">
          <el-pagination
            v-if="total > 0"
            v-model:current-page="page"
            v-model:page-size="size"
            :page-sizes="[12, 24, 48]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @current-change="load"
            @size-change="reload"
          />
        </div>
      </div>

      <aside class="guide-aside">
        <div class="aside-card">
          <div class="aside-title">数据来源</div>
          <p class="aside-text">忍者数据与技能来自火影忍者OL官网（bang.qq.com），已缓存到本地，头像与技能图标下载压缩存储。</p>
        </div>

        <div class="aside-card">
          <div class="aside-title">使用说明</div>
          <ul class="aside-tips">
            <li><el-icon><Pointer /></el-icon><span>鼠标悬停忍者卡片查看技能</span></li>
            <li><el-icon><Refresh /></el-icon><span>管理员可手动刷新（增量）</span></li>
            <li><el-icon><FirstAidKit /></el-icon><span>缺失技能自动隐藏</span></li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getNinjaList, refreshNinjas } from '@/api/hyolNinja'
import type { HyolNinja, NinjaSkill } from '@/types/hyolNinja'

const userStore = useUserStore()

const list = ref<HyolNinja[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(24)
const loading = ref(false)
const refreshing = ref(false)

const keyword = ref('')
const attr = ref('')
const hurtType = ref('')
const chaseStatus = ref('')
const hurtStatus = ref('')
const rare = ref('')

const ATTRIBUTES = ['火', '水', '风', '雷', '土']
const HURT_TYPES = ['体', '忍']
const CHASE_STATUSES = ['倒地', '击退', '小浮空', '大浮空', '高连击', '定身', '睡眠']
const HURT_STATUSES = ['点燃', '中毒', '混乱', '定身', '麻痹', '封穴', '标记', '睡眠', '目盲', '减速', '打断']
const RARITIES = ['蓝', '紫', '橙']

const KEYWORD_COLOR = '#4dc831'
const INSTANT_COLOR = '#ffba00'

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function skillDescHtml(s: NinjaSkill): string {
  const desc = escapeHtml(s.desc || '')
  const colored = desc.replace(/\[([^\]]+)\]/g, (_m, kw: string) => {
    return `<span style="color:${KEYWORD_COLOR}">[${kw}]</span>`
  })
  const instant = s.moment === '1'
    ? `<span style="color:${INSTANT_COLOR}">[瞬发]</span>`
    : ''
  return instant + colored
}

const load = async () => {
  loading.value = true
  try {
    const res = await getNinjaList(
      page.value,
      size.value,
      keyword.value,
      attr.value,
      hurtType.value,
      chaseStatus.value,
      hurtStatus.value,
      rare.value
    )
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  load()
}

let searchTimer: ReturnType<typeof setTimeout> | undefined
const onSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(reload, 300)
}

// ===== 技能浮层跟随鼠标定位（翻转 + 纵向夹取，避免超出视口） =====
const activeCard: { card: HTMLElement | null; popover: HTMLElement | null } = { card: null, popover: null }
let mouseX = 0
let mouseY = 0
let rafId: number | undefined

const POP_GAP = 14
const POP_MARGIN = 8

function positionPopover() {
  const { popover } = activeCard
  if (!popover) return
  const popW = popover.offsetWidth
  const popH = popover.offsetHeight

  // 水平：以鼠标为基准，右侧放得下向右，否则向左
  let left: number
  if (mouseX + POP_GAP + popW <= window.innerWidth - POP_MARGIN) {
    left = mouseX + POP_GAP
  } else {
    left = mouseX - POP_GAP - popW
  }
  left = Math.max(POP_MARGIN, left)

  // 纵向：跟随鼠标，超底时向上夹取
  let top = mouseY + POP_GAP
  if (top + popH > window.innerHeight - POP_MARGIN) {
    top = mouseY - POP_GAP - popH
  }
  top = Math.max(POP_MARGIN, top)

  popover.style.left = `${left}px`
  popover.style.top = `${top}px`
}

function onCardEnter(event: MouseEvent) {
  const card = event.currentTarget as HTMLElement
  const popover = card.querySelector('.skill-popover') as HTMLElement | null
  if (!popover) return
  activeCard.card = card
  activeCard.popover = popover
  mouseX = event.clientX
  mouseY = event.clientY
  popover.style.position = 'fixed'
  positionPopover()
}

function onCardMove(event: MouseEvent) {
  if (!activeCard.popover) return
  mouseX = event.clientX
  mouseY = event.clientY
  scheduleReposition()
}

function onCardLeave() {
  activeCard.card = null
  activeCard.popover = null
}

const scheduleReposition = () => {
  if (rafId !== undefined) return
  rafId = requestAnimationFrame(() => {
    rafId = undefined
    positionPopover()
  })
}

onBeforeUnmount(() => {
  if (rafId !== undefined) cancelAnimationFrame(rafId)
})

const onRefresh = async () => {
  refreshing.value = true
  try {
    const res = await refreshNinjas()
    ElMessage.success(`刷新完成：成功 ${res.data?.fetched ?? 0} 条，失败 ${res.data?.failed ?? 0} 条`)
    await reload()
  } catch {
    // 错误已由拦截器提示
  } finally {
    refreshing.value = false
  }
}

load()
</script>

<style scoped lang="scss">
.ninja-guide {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;

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

  .guide-layout {
    margin-top: 18px;
    display: flex;
    gap: 20px;
    align-items: flex-start;
  }

  .guide-main {
    flex: 1;
    min-width: 0;
  }

  .filter-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 14px;

    .filter-search { width: 260px; }
    .filter-attr { width: 130px; }
  }

  .ninja-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 14px;
    min-height: 120px;
  }

  .ninja-card {
    position: relative;
    padding: 14px 10px 12px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    border-radius: 12px;
    text-align: center;
    transition: box-shadow 0.25s var(--ev-ease-out);

    &:hover {
      z-index: 20;
      box-shadow: var(--ev-shadow-sm);

      .skill-popover {
        display: block;
      }
    }

    .card-avatar {
      width: 96px;
      height: 128px;
      margin: 0 auto;
      border-radius: 10px;
      overflow: hidden;
      background: var(--ev-bg-tint);

      img {
        width: 100%;
        height: 100%;
        object-fit: contain;
        object-position: center;
        display: block;
      }
    }

    .card-name {
      margin-top: 10px;
      font-size: 13px;
      font-weight: 600;
      color: var(--ev-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .card-tags {
      margin-top: 6px;
      display: flex;
      justify-content: center;
      gap: 6px;

      .tag {
        font-size: 11px;
        padding: 1px 7px;
        border-radius: 8px;

        &.attr {
          color: var(--ev-primary);
          background: rgba(47, 124, 246, 0.1);
        }

        &.star {
          color: #d98a1f;
          background: rgba(217, 138, 31, 0.12);
        }
      }
    }
  }

  /* 悬停技能浮层：默认隐藏（不影响布局），悬停时 JS 定位为 fixed 并翻转/夹取 */
  .skill-popover {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 1000;
    width: fit-content;
    min-width: 260px;
    max-width: 360px;
    max-height: calc(100vh - 16px);
    padding: 14px;
    text-align: left;
    background: var(--ev-bg-surface);
    border: 1px solid var(--ev-border-default);
    border-radius: 12px;
    box-shadow: var(--ev-shadow-float), var(--ev-inset-gloss);
    display: none;
    pointer-events: none;

    .pop-header {
      margin-bottom: 10px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--ev-border-subtle);

      .pop-name {
        font-size: 14px;
        font-weight: 700;
        color: var(--ev-text-primary);
      }
    }

    .skill-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .skill-item {
      display: flex;
      gap: 10px;
      align-items: flex-start;

      .skill-icon {
        width: 40px;
        height: 40px;
        flex-shrink: 0;
        border-radius: 6px;
        object-fit: contain;
        background: var(--ev-bg-tint);
      }

      .skill-body {
        min-width: 0;
        flex: 1;

        .skill-head {
          display: flex;
          align-items: center;
          gap: 6px;

          .skill-type {
            font-size: 11px;
            padding: 0 6px;
            border-radius: 6px;
            color: #fff;
            background: var(--ev-primary);
            flex-shrink: 0;
          }

          .skill-title {
            font-size: 13px;
            font-weight: 600;
            color: var(--ev-text-primary);
          }
        }

        .skill-desc {
          margin-top: 3px;
          font-size: 12px;
          line-height: 1.6;
          color: var(--ev-text-regular);
          word-break: break-word;
        }
      }
    }

    .pop-empty {
      font-size: 12px;
      color: var(--ev-text-muted);
      padding: 8px 0;
    }

    .pop-getway {
      margin-top: 10px;
      padding-top: 8px;
      border-top: 1px solid var(--ev-border-subtle);
      font-size: 12px;
      color: var(--ev-text-secondary);
      word-break: break-word;
    }
  }

  .grid-empty {
    grid-column: 1 / -1;
  }

  .list-footer {
    margin-top: 16px;
    display: flex;
    justify-content: center;
  }

  .guide-aside {
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

  .aside-text {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: var(--ev-text-regular);
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
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: var(--ev-text-regular);

      .el-icon {
        color: var(--ev-primary);
        flex-shrink: 0;
      }
    }
  }

  @media (max-width: 900px) {
    .guide-layout {
      flex-direction: column;
    }

    .guide-aside {
      width: 100%;
      position: static;
    }
  }
}
</style>
