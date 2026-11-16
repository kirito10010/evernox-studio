<template>
  <div class="ninja-guide">
    <section class="codex-hero">
      <span class="hero-mark" aria-hidden="true">忍</span>
      <div class="hero-content">
        <div class="hero-top">
          <div class="hero-heading">
            <div class="hero-kicker">NARUTO ONLINE · 忍界资料</div>
            <h1>忍者图鉴</h1>
          </div>
          <div class="hero-stats">
            <span class="stat"><strong>{{ total }}</strong><span>忍者</span></span>
            <span class="stat"><strong>{{ skillTotal }}</strong><span>技能</span></span>
            <span class="stat"><strong>{{ formTotal }}</strong><span>形态</span></span>
            <span class="stat"><strong>{{ filtered.length }}</strong><span>当前结果</span></span>
          </div>
          <div class="hero-actions">
            <span v-if="importing" class="import-progress">{{ importProgress }}%</span>
            <template v-if="isAdmin">
              <el-button size="small" :loading="importing" @click="pickFolder">导入抓包</el-button>
            </template>
            <input
              ref="folderInput"
              type="file"
              webkitdirectory
              multiple
              class="folder-input"
              @change="onFolderChange"
            />
          </div>
        </div>
        <p>默认展示初始形态 · 悬停角色预览技能 · 点击查看突破表</p>
      </div>
    </section>

    <div class="codex-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索忍者 / 技能名 / 技能描述"
        clearable
        size="small"
        class="tool-search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="rare" placeholder="稀有度" clearable size="small" class="tool-select">
        <el-option label="蓝" :value="2" />
        <el-option label="紫" :value="3" />
        <el-option label="橙" :value="4" />
      </el-select>

      <el-select v-model="star" placeholder="星级" clearable size="small" class="tool-select">
        <el-option label="1 星" :value="1" />
        <el-option label="2 星" :value="2" />
        <el-option label="3 星" :value="3" />
        <el-option label="4 星" :value="4" />
      </el-select>

      <el-select v-model="attributeFilter" placeholder="属性" clearable size="small" class="tool-select">
        <el-option v-for="a in ATTRIBUTES" :key="a" :label="a" :value="a" />
      </el-select>

      <el-select v-model="officialTag" placeholder="官方标签" clearable filterable size="small" class="tool-official">
        <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
      </el-select>

      <el-select v-model="opTag" placeholder="运营标签" clearable size="small" class="tool-select">
        <el-option v-for="t in OP_TAGS" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>


      <el-select v-model="breakFilter" placeholder="突破" clearable size="small" class="tool-select">
        <el-option label="未突破" value="none" />
        <el-option label="已突破" value="yes" />
      </el-select>

      <el-select v-model="skillKeyword" placeholder="技能关键词" clearable filterable size="small" class="tool-keyword">
        <el-option v-for="k in SKILL_KEYWORDS" :key="k" :label="k" :value="k" />
      </el-select>

      <el-select v-model="sortBy" size="small" class="tool-sort">
        <el-option label="默认排序" value="default" />
        <el-option label="稀有度优先" value="rare" />
        <el-option label="星级优先" value="star" />
        <el-option label="名称排序" value="name" />
      </el-select>

      <el-select v-model="variantFilter" placeholder="版本特性" clearable size="small" class="tool-variant">
        <el-option label="有突破" value="break" />
        <el-option label="有变异 Y" value="y" />
        <el-option label="有链接 L" value="l" />
        <el-option label="有变身" value="transform" />
        <el-option label="有星级变化" value="star" />
      </el-select>

      <el-button size="small" class="tool-reset" @click="resetFilters">重置</el-button>
      <el-button size="small" class="tool-random" :icon="RefreshRight" @click="randomNinja">随机</el-button>
    </div>

    <div v-loading="loading" class="codex-grid">
      <div v-if="error" class="grid-state">
        <el-empty :description="loadError || '忍者数据加载失败'">
          <el-button type="primary" @click="load">重新加载</el-button>
        </el-empty>
      </div>

      <template v-else>
        <article
          v-for="n in paged"
          :key="n.id"
          class="ninja-card"
          tabindex="0"
          :aria-label="`查看 ${n.name} 的技能突破`"
          @click="openDetail(n)"
          @keydown.enter="openDetail(n)"
          @mouseenter="onHoverEnter(n, $event)"
          @mousemove="onHoverMove($event)"
          @mouseleave="hideHoverNow"
        >
          <div class="portrait">
            <span class="aura" aria-hidden="true"></span>
            <img
              v-if="!n.portraitFailed"
              :src="portraitSrc(n)"
              :alt="n.name"
              loading="lazy"
              @error="onPortraitError(n)"
            />
            <span v-else class="portrait-fallback">{{ n.name.slice(0, 1) }}</span>

            <div class="portrait-top">
              <span class="rare-tag" :class="`rare-${n.rare}`">{{ rareText(n.rare) }}</span>
              <span class="star-tag">{{ '★'.repeat(n.star) }}</span>
            </div>

            <div v-if="hasVariantFlags(n)" class="variant-flags">
              <span v-if="n.flags.star" class="vf star" title="有星级变化">星</span>
              <span v-if="n.flags.break" class="vf break" title="有技能突破">+</span>
              <span v-if="n.flags.y" class="vf y" title="有变异 Y 技能">Y</span>
              <span v-if="n.flags.l" class="vf l" title="有链接 L 技能">L</span>
              <span v-if="n.flags.transform" class="vf transform" title="有变身形态">变</span>
            </div>

            <div class="portrait-hover">
              <span class="hint">悬停预览 · 点击突破 →</span>
            </div>
          </div>
          <div class="card-name" :title="n.name">{{ n.name }}</div>
        </article>
      </template>

      <el-empty
        v-if="!loading && !error && !filtered.length"
        class="grid-state"
        description="没有匹配的忍者"
      />
    </div>

    <div class="list-footer">
      <el-pagination
        v-if="filtered.length"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[12, 24, 48]"
        :total="filtered.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="onSizeChange"
      />
    </div>

    <div class="codex-foot">
      数据来源：火影忍者OL 客户端配置（NinjaInfo / NinjaSkill / SkillCFG / SkillExState / SkillKeyDesc）
      · 抓包导入 {{ codexBuild }}
      <span v-if="codexOutdated" class="codex-stale">（导入的数据集版本过旧，请重新导入抓包）</span>
    </div>

    <Teleport to="body">
      <div v-if="hoverNinja" class="ninja-hover-layer">
        <section
          v-if="hoverTransformSkills.length"
          class="hover-panel hover-transform"
          :style="hoverTransformStyle"
        >
          <div class="hp-title">
            变身后技能
            <span class="hp-badge">变身</span>
          </div>
          <div class="hp-skills">
            <div v-for="s in hoverTransformSkills" :key="`t-${s.id}`" class="hp-skill">
              <span class="hp-role" :class="typeClass(s.type)">{{ s.type }}</span>
              <span class="hp-icon" :class="typeClass(s.type)">
                <img v-if="activeIcon(s)" :src="asset(activeIcon(s))" alt="" @error="onIconError(activeIcon(s))" />
                <span v-else>{{ typeShort(s.type) }}</span>
              </span>
              <div class="hp-body">
                <div class="hp-name-row">
                  <span class="hp-name">{{ s.name || s.type }}</span>
                </div>
                <div class="hp-stats">冷却回合:{{ s.cd }} · 进场冷却:{{ s.enterCd }} · 消耗查克拉:{{ s.mp }}</div>
                <div class="hp-desc" v-html="renderDesc(s.desc)"></div>
              </div>
            </div>
          </div>
        </section>

        <section
          v-if="hoverStatuses.length"
          class="hover-panel hover-status"
          :style="hoverStatusStyle"
        >
          <div class="hp-title">状态介绍</div>
          <div class="hp-status-list">
            <div v-for="st in hoverStatuses" :key="`st-${st.name}`" class="hp-status">
              <span class="hp-status-name">{{ st.name }}</span>
              <span class="hp-status-desc">{{ st.desc }}</span>
            </div>
          </div>
        </section>

        <section
          class="hover-panel hover-main"
          :style="hoverMainStyle"
        >
          <div class="hp-head">
            <span class="hp-avatar">
              <img v-if="hoverNinja.head" :src="headSrc(hoverNinja)" alt="" @error="onHeadError(hoverNinja)" />
              <span v-else>{{ hoverNinja.name.slice(0, 1) }}</span>
            </span>
            <div class="hp-meta">
              <div class="hp-ninja-name">{{ hoverNinja.name }}</div>
              <div class="hp-sub">
                <span class="hp-stars">{{ '★'.repeat(hoverNinja.star) }}</span>
                <span>{{ rareText(hoverNinja.rare) }}</span>
                <span v-if="hoverNinja.transform" class="hp-transform">可变身</span>
              </div>
            </div>
          </div>
          <div class="hp-skills">
            <div v-for="s in hoverMainSkills" :key="`m-${s.id}`" class="hp-skill">
              <span class="hp-role" :class="typeClass(s.type)">{{ s.type }}</span>
              <span class="hp-icon" :class="typeClass(s.type)">
                <img v-if="activeIcon(s)" :src="asset(activeIcon(s))" alt="" @error="onIconError(activeIcon(s))" />
                <span v-else>{{ typeShort(s.type) }}</span>
              </span>
              <div class="hp-body">
                <div class="hp-name-row">
                  <span class="hp-name">{{ s.name || s.type }}</span>
                </div>
                <div class="hp-stats">冷却回合:{{ s.cd }} · 进场冷却:{{ s.enterCd }} · 消耗查克拉:{{ s.mp }}</div>
                <div class="hp-desc" v-html="renderDesc(s.desc)"></div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </Teleport>

    <el-dialog
      v-model="detailVisible"
      width="1120px"
      append-to-body
      class="ninja-codex-dialog"
    >
      <template #header>
        <div v-if="detail" class="detail-header">
          <div class="detail-title-wrap">
            <span class="detail-head-avatar">
              <img v-if="detail.head" :src="asset(detail.head)" alt="" />
              <span v-else>{{ detail.name.slice(0, 1) }}</span>
            </span>
            <div class="detail-title-text">
              <div class="detail-name">{{ detail.name }}</div>
              <div class="detail-sub">
                <span class="detail-stars">{{ '★'.repeat(detail.star) }}</span>
                <span>{{ rareText(detail.rare) }}</span>
                <span v-if="allForms.length > 1">{{ allForms.length }} 个形态</span>
                <span v-if="detail.flags.break && detail.breakthroughStar > 0" class="detail-break">突破条件：{{ detail.breakthroughStar }}星</span>
                <span v-if="detail.transform" class="detail-transform">可变身</span>
              </div>
            </div>
          </div>
          <div v-if="allForms.length > 1" class="form-tabs">
            <button
              v-for="(f, i) in allForms"
              :key="f.key"
              type="button"
              class="form-tab"
              :class="{ active: activeFormIndex === i }"
              @click="activeFormIndex = i"
            >
              {{ f.label }}
            </button>
          </div>
        </div>
      </template>

      <div v-if="detail" class="detail-body">
        <aside class="detail-left">
          <div class="detail-portrait">
            <span class="aura" aria-hidden="true"></span>
            <img
              v-if="!detail.portraitFailed"
              :src="portraitSrc(detail)"
              :alt="detail.name"
              @error="onPortraitError(detail)"
            />
            <span v-else class="portrait-fallback">{{ detail.name.slice(0, 1) }}</span>
          </div>
        </aside>

        <main class="detail-right">
          <section class="break-block">
            <div class="panel-title">
              {{ activeFormIsExtra ? '变身后技能组' : '技能突破' }}
              <span
                v-if="!activeFormIsExtra && detail.flags.break && detail.breakthroughStar > 0"
                class="break-condition"
              >{{ detail.breakthroughStar }}星解锁</span>
              <span v-if="activeFormIsExtra" class="break-hint">变身后技能组不参与技能突破</span>
              <span v-else class="break-hint">悬停图标查看技能详情</span>
            </div>

            <div v-if="activeFormIsExtra" class="extra-skill-list">
              <div
                v-for="(slot, idx) in tableRows"
                :key="`${slot.role}-${slot.initial}-${idx}`"
                class="extra-skill"
              >
                <span class="role-cell" :class="typeClass(slot.role)">{{ slot.role }}</span>
                <span class="extra-icon" :class="typeClass(slot.role)">
                  <img
                    v-if="activeIcon(slotSkill(slot))"
                    :src="asset(activeIcon(slotSkill(slot)))"
                    :alt="slot.role"
                    @error="onIconError(activeIcon(slotSkill(slot)))"
                  />
                  <span v-else>{{ typeShort(slot.role) }}</span>
                </span>
                <div class="extra-body">
                  <div class="extra-name">{{ slotSkill(slot)?.name || slot.role }}</div>
                  <div class="extra-stats">
                    冷却回合:{{ slotSkill(slot)?.cd ?? 0 }}
                    · 进场冷却:{{ slotSkill(slot)?.enterCd ?? 0 }}
                    · 消耗查克拉:{{ slotSkill(slot)?.mp ?? 0 }}
                  </div>
                  <div class="extra-desc" v-html="renderDesc(slotSkill(slot)?.desc || '')"></div>
                </div>
              </div>
            </div>

            <div v-else class="break-scroll">
              <table class="break-table">
                <thead>
                  <tr>
                    <th class="role-col">技能</th>
                    <th>初始</th>
                    <th v-for="i in breakColumns" :key="`h-${i}`">突破 {{ i }}</th>
                    <th v-if="hasYColumn">异变 Y</th>
                    <th v-if="hasLColumn">链接 L</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(slot, idx) in tableRows" :key="`${slot.role}-${slot.initial}-${idx}`">
                    <td class="role-col">
                      <span class="role-cell" :class="typeClass(slot.role)">{{ slot.role }}</span>
                    </td>

                    <td class="cell initial-cell">
                      <SkillIcon
                        :skill="slotSkill(slot)"
                        :icon-url="slotIcon(slot)"
                        :icon-fallbacks="slotIconFallbacks(slot)"
                        size="md"
                      />
                      <span class="chosen">已选</span>
                    </td>

                    <td v-for="i in breakColumns" :key="`c-${i}`" class="cell">
                      <SkillIcon
                        v-if="breakIdAt(slot, i)"
                        :skill="variantSkill(breakIdAt(slot, i))"
                        :icon-url="variantIcon(breakIdAt(slot, i))"
                        :icon-fallbacks="variantIconFallbacks(breakIdAt(slot, i))"
                        :base-skill="slotSkill(slot)"
                        :badge="`+${i}`"
                        size="sm"
                      />
                      <span v-else class="cell-empty">—</span>
                    </td>

                    <td v-if="hasYColumn" class="cell">
                      <SkillIcon
                        v-if="slot.y"
                        :skill="variantSkill(slot.y)"
                        :icon-url="variantIcon(slot.y)"
                        :icon-fallbacks="variantIconFallbacks(slot.y)"
                        :base-skill="slotSkill(slot)"
                        badge="Y"
                        size="sm"
                      />
                      <span v-else class="cell-empty">—</span>
                    </td>

                    <td v-if="hasLColumn" class="cell">
                      <SkillIcon
                        v-if="slot.l"
                        :skill="variantSkill(slot.l)"
                        :icon-url="variantIcon(slot.l)"
                        :icon-fallbacks="variantIconFallbacks(slot.l)"
                        :base-skill="slotSkill(slot)"
                        badge="L"
                        size="sm"
                      />
                      <span v-else class="cell-empty">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </main>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SkillIcon from '@/components/ninja/SkillIcon.vue'
import { useUserStore } from '@/stores/user'
import {
  codexImageUrl,
  getCodexDataset,
  importCodexFolder,
} from '@/api/hyolCodex'
import type { CodexDataset, CodexForm, CodexNinja, CodexSlot } from '@/types/ninjaCodex'

interface SkillState {
  name: string
  desc: string
}

interface NinjaSkill {
  id: string
  name: string
  desc: string
  type: string
  icon: string
  /** 图标兜底链（客户端没有打包部分技能图标） */
  iconFallback?: string[]
  cd: number
  enterCd: number
  mp: number
  immediately: boolean
  states: SkillState[]
  linkName?: string
  linkDesc?: string
}

interface SkillExtra {
  /** 原始附加技能 id，仅用于区分类形态 */
  id?: string
  desc: string
  states: SkillState[]
}

interface SkillSlot {
  role: string
  initial: string
  /** 突破阶梯，顺序即 +1、+2… */
  breakList?: string[]
  y?: string
  l?: string
  /** 同一被动/技能被拆成“基础 + 此外…”时的附加效果，展示时合并到初始技能 */
  extraSkills?: SkillExtra[]
}

interface NinjaForm {
  key: string
  label: string
  star: number
  kind?: 'base' | 'star' | 'extra'
  slots: SkillSlot[]
}

interface NinjaTransform {
  label: string
  slots: SkillSlot[]
}

interface NinjaFlags {
  star: boolean
  break: boolean
  y: boolean
  l: boolean
  transform: boolean
}

interface Ninja {
  id: string
  name: string
  star: number
  rare: number
  portrait: string
  head: string
  breakthroughStar: number
  attribute?: string
  tags?: string[]
  flags: NinjaFlags
  forms: NinjaForm[]
  transform: NinjaTransform | null
  /** 静态立绘缺失时，才改用后端抓包落盘的立绘 */
  portraitUseBackend?: boolean
  /** 静态头像缺失时，才改用后端抓包落盘的立绘 */
  headUseBackend?: boolean
  portraitFailed?: boolean
}

const BASE = import.meta.env.BASE_URL.endsWith('/')
  ? import.meta.env.BASE_URL
  : `${import.meta.env.BASE_URL}/`

// 数据集结构变更后递增，避免浏览器或 Nginx 继续使用旧缓存
const ASSET_VERSION = '9'
const asset = (p: string) => (p ? `${BASE}ninja/${p}?v=${ASSET_VERSION}` : '')

const SKILL_KEYWORDS = [
  '点燃', '中毒', '混乱', '定身', '麻痹', '封穴', '标记', '睡眠', '目盲', '减速',
  '打断', '击退', '倒地', '小浮空', '大浮空', '高连击', '护盾', '结界',
]

const ATTRIBUTES = ['水', '火', '风', '雷', '土']

/**
 * 运营标签。客户端配置里只有两类有数据源：
 * - 变身忍者：NinjaInfoCFG/NinjaSkillCFG 推导出的变身后形态；
 * - 赛季忍者：seasonTask/SeasonTaskNinjaInfoCFG 的忍者集合。
 * 花榜 / 升旗 / 精品店 / 点券 / 元宝 在客户端 65 张配置表中 0 命中，无法从抓包推导。
 */
const OP_TAGS: Array<{ label: string; value: 'transform' | 'season' }> = [
  { label: '变身忍者', value: 'transform' },
  { label: '赛季忍者', value: 'season' },
]

/** 官方标签固定顺序（与游戏内 property/*.png 顺序一致） */
const TAG_ORDER = [
  '木叶', '云隐', '雾隐', '岩隐', '砂隐', '音隐', '雨隐', '泷隐', '影',
  '宇智波一族', '日向一族', '千手一族', '漩涡一族', '奈良一族', '山中一族', '秋道一族',
  '大筒木一族', '秽土转生', '暗部', '晓', '咒印', '带刀', '死司凭血', '傀儡', '通灵',
  '神秘', '人柱力', '血继限界', '轮回眼', '秘传技', '忍联合军', '仙术', '血继淘汰',
  '血继网罗', '佐助真传', '男性', '雄性', '女性', '雌性', '新春', '夏装', '泳装',
  '中秋', '冬日', '中国风', '西装', '异装', '和服', '武士', '博人传', '赛季',
]

const RARE_TEXT: Record<number, string> = { 2: '蓝', 3: '紫', 4: '橙' }
const TYPE_CLASS: Record<string, string> = {
  奥义: 'ultimate',
  普攻: 'normal',
  追打: 'chase',
  触发: 'trigger',
  被动: 'passive',
  其他: 'other',
}
const TYPE_SHORT: Record<string, string> = {
  奥义: '奥',
  普攻: '普',
  追打: '追',
  触发: '触',
  被动: '被',
  其他: '技',
}

const rareText = (r: number) => RARE_TEXT[r] || '—'
const typeClass = (t: string) => TYPE_CLASS[t] || 'other'
const typeShort = (t: string) => TYPE_SHORT[t] || '技'
const stripTags = (s: string) => String(s || '').replace(/<[^>]*>/g, '')

/** 只保留 <font color>，用于悬浮面板里的技能描述 */
function renderDesc(desc: string): string {
  let s = String(desc || '')
    .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>').replace(/&quot;/gi, '"').replace(/&#39;/g, "'")
    .replace(/&amp;/gi, '&')
    .replace(/<br\s*\/?>/gi, '\n')
  const tokens: string[] = []
  s = s.replace(/<\s*\/?\s*font[^>]*>/gi, m => {
    let out = ''
    if (/^<\s*\//.test(m)) {
      out = '</font>'
    } else {
      const c = /color\s*=\s*["']?(#[0-9a-fA-F]{3,8})/i.exec(m)
      if (c) out = `<font color="${c[1]}">`
    }
    tokens.push(out)
    return `\u0000${tokens.length - 1}\u0000`
  })
  s = s.replace(/<[^>]*>/g, '')
  s = s.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  s = s.replace(/&(?!(amp|lt|gt|quot|#\d+);)/g, '&amp;')
  s = s.replace(/\u0000(\d+)\u0000/g, (_, i) => tokens[Number(i)])
  return s.replace(/\n/g, '<br>')
}

// ===== 数据加载：只使用后端抓包导入的数据集 =====
// 后端推导逻辑变更时递增；低于该版本的数据集直接忽略，避免旧数据（例如误判的变身后形态）继续生效
const MIN_CODEX_DATASET_VERSION = 9

const all = ref<Ninja[]>([])
const skills = ref<Record<string, NinjaSkill>>({})
const loading = ref(false)
const error = ref(false)
const loadError = ref('')
const codexBuild = ref('')
const codexOutdated = ref(false)
let loadPromise: Promise<Ninja[]> | null = null

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

function isBackendDataset(data: CodexDataset): boolean {
  if (!data || !Array.isArray(data.ninjas) || !data.skills || !data.keywords) {
    return false
  }
  if ((data.version || 0) < MIN_CODEX_DATASET_VERSION) {
    codexOutdated.value = true
    return false
  }
  return true
}

function toInternalStates(names: string[] | undefined, keywords: Record<string, string[]>): SkillState[] {
  return (names || []).map(name => ({ name, desc: (keywords[name] || [])[0] || '' }))
}

function slotFromCodex(slot: CodexSlot): SkillSlot {
  return {
    role: slot.role,
    initial: slot.initial,
    breakList: slot.break || [],
    y: slot.y,
    l: slot.l,
  }
}

function formFromCodex(form: CodexForm): NinjaForm {
  return {
    key: form.key,
    label: form.label,
    star: form.star,
    kind: form.kind,
    slots: form.slots.map(slotFromCodex),
  }
}

function adaptBackendDataset(data: CodexDataset): Ninja[] {
  const adapted: Record<string, NinjaSkill> = {}
  for (const [id, skill] of Object.entries(data.skills)) {
    adapted[id] = {
      id,
      name: skill.name || skill.role,
      desc: skill.desc || '',
      type: skill.role || '其他',
      icon: skill.icon || '',
      iconFallback: skill.iconFallback || [],
      cd: skill.cd || 0,
      enterCd: skill.enterCd || 0,
      mp: skill.mp || 0,
      immediately: !!skill.immediately,
      states: toInternalStates(skill.states, data.keywords),
    }
  }
  skills.value = adapted
  return data.ninjas.map((n: CodexNinja) => {
    const forms = n.forms.map(formFromCodex)
    const extraForms = (n.extraForms || []).map(formFromCodex)
    return {
      id: n.id,
      name: n.name,
      star: n.star,
      rare: n.rare,
      portrait: n.portrait,
      head: n.head,
      breakthroughStar: n.breakthroughStar,
      attribute: n.attribute || '',
      tags: n.tags || [],
      flags: { ...n.flags, transform: extraForms.length > 0 },
      forms,
      transform: extraForms[0] ? { label: extraForms[0].label, slots: extraForms[0].slots } : null,
    }
  })
}

async function fetchNinjas(): Promise<Ninja[]> {
  codexOutdated.value = false
  let data: CodexDataset
  try {
    data = await getCodexDataset()
  } catch {
    throw new Error('尚未导入抓包数据，请管理员导入')
  }
  if (!isBackendDataset(data)) {
    throw new Error(codexOutdated.value
      ? '导入的数据集版本过旧，请管理员重新导入抓包'
      : '数据集格式不正确，请管理员重新导入抓包')
  }
  codexBuild.value = data.build || ''
  return adaptBackendDataset(data)
}

async function load() {
  loading.value = true
  error.value = false
  loadError.value = ''
  try {
    if (!loadPromise) loadPromise = fetchNinjas()
    all.value = await loadPromise
  } catch (e) {
    error.value = true
    loadError.value = e instanceof Error ? e.message : '忍者数据加载失败'
    loadPromise = null
  } finally {
    loading.value = false
  }
}

// ===== 数据集图片：优先站点静态立绘，静态缺失时才用抓包落盘的图 =====
// 抓包里的 ninjaBigImg 是「半身 + 白底 + 靠右」的游戏内素材，和站点精选立绘风格不一致，
// 直接覆盖会让卡片看起来很怪，所以只把它当作静态图缺失时的兜底。
function portraitSrc(n: Ninja): string {
  if (n.portraitUseBackend) {
    return codexImageUrl(`${n.id}.png`)
  }
  return asset(n.portrait)
}

function headSrc(n: Ninja): string {
  if (n.headUseBackend) {
    return codexImageUrl(`${n.id}.png`)
  }
  return asset(n.head)
}

function onPortraitError(n: Ninja) {
  // 第一次失败：静态立绘没有 → 尝试抓包落盘图；仍失败才显示首字占位
  if (!n.portraitUseBackend) {
    n.portraitUseBackend = true
    return
  }
  n.portraitFailed = true
}

function onHeadError(n: Ninja) {
  if (!n.headUseBackend) {
    n.headUseBackend = true
    return
  }
  n.head = ''
}

// ===== 抓包导入（仅管理员） =====
const folderInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const importProgress = ref(0)

function pickFolder() {
  folderInput.value?.click()
}

async function onFolderChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return

  const jsonl = files.filter(file => file.name.toLowerCase().endsWith('.jsonl'))
  if (!jsonl.length) {
    ElMessage.warning('所选文件夹里没有 http-*.jsonl（netlog 不需要上传）')
    return
  }
  const totalBytes = jsonl.reduce((sum, file) => sum + file.size, 0)
  if (totalBytes > 200 * 1024 * 1024) {
    ElMessage.warning('抓包文件总大小超过 200MB，请只保留最新的 http-*.jsonl')
    return
  }

  const formData = new FormData()
  jsonl.forEach(file => formData.append('files', file, file.name))

  importing.value = true
  importProgress.value = 0
  try {
    const res = await importCodexFolder(formData, percent => {
      importProgress.value = percent
    })
    const data = res.data
    if (data) {
      const warn = data.warnings?.length ? `，${data.warnings.length} 条提示` : ''
      ElMessage.success(
        `导入完成：${data.ninjaCount} 位忍者 / ${data.skillCount} 个技能 / ${data.formCount} 个形态${warn}`
      )
    }
    await refreshDataset()
  } catch {
    // 请求层已提示
  } finally {
    importing.value = false
    importProgress.value = 0
  }
}

async function refreshDataset() {
  loadPromise = null
  resetFilters()
  await load()
}

const route = useRoute()

onMounted(async () => {
  await load()
  const id = String(route.query.ninja || '')
  if (id) {
    const n = all.value.find(x => x.id === id)
    if (n) openDetail(n)
  }
  window.addEventListener('scroll', hideHoverNow, true)
})

onUnmounted(() => {
  window.removeEventListener('scroll', hideHoverNow, true)
})

// ===== 筛选与排序 =====
const keyword = ref('')
const searchKeyword = ref('')
const rare = ref<number | ''>('')
const star = ref<number | ''>('')
const skillKeyword = ref('')
const sortBy = ref<'default' | 'rare' | 'star' | 'name'>('default')
const variantFilter = ref<'' | 'break' | 'y' | 'l' | 'transform' | 'star'>('')
const attributeFilter = ref('')
const officialTag = ref('')
const opTag = ref<'' | 'transform' | 'season'>('')
const breakFilter = ref<'' | 'yes' | 'none'>('')

const allTags = computed(() => {
  const present = new Set<string>()
  for (const n of all.value) {
    for (const tag of n.tags || []) present.add(tag)
  }
  const known = TAG_ORDER.filter(tag => present.has(tag))
  const extra = [...present]
    .filter(tag => !TAG_ORDER.includes(tag))
    .sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
  return [...known, ...extra]
})

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, v => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = v
  }, 250)
})

const skillById = (id?: string | null): NinjaSkill | null => (id ? skills.value[id] || null : null)
const iconUrl = (s: NinjaSkill | null) => (s?.icon ? asset(s.icon) : '')
/** 图标兜底链：主图标 404 时依次尝试的静态图标 */
const iconFallbackUrls = (s: NinjaSkill | null) =>
  (s?.iconFallback || []).filter(Boolean).map(asset)

/** 把槽位上的附加效果合并进初始技能（如法一 4 星“分福的传承” + “此外…”） */
function slotDisplaySkill(slot: SkillSlot): NinjaSkill | null {
  const base = skillById(slot.initial)
  if (!base) return null
  const extras = slot.extraSkills || []
  if (!extras.length) return base

  let desc = base.desc || ''
  const states = [...(base.states || [])]
  for (const extra of extras) {
    if (!extra.desc) continue
    const extraPlain = stripTags(extra.desc || '').trim()
    if (extraPlain && !stripTags(desc).includes(extraPlain.slice(0, 24))) {
      desc = desc ? `${desc}\n${extra.desc}` : extra.desc
    }
  }
  for (const extra of extras) {
    for (const st of extra.states || []) {
      if (!states.some(x => x.name === st.name)) states.push(st)
    }
  }
  return { ...base, desc, states }
}

const slotSkill = (slot: SkillSlot) => slotDisplaySkill(slot)
const slotIcon = (slot: SkillSlot) => iconUrl(slotDisplaySkill(slot))
const slotIconFallbacks = (slot: SkillSlot) => iconFallbackUrls(slotDisplaySkill(slot))
const variantSkill = (id?: string) => skillById(id)
const variantIcon = (id?: string) => iconUrl(skillById(id))
const variantIconFallbacks = (id?: string) => iconFallbackUrls(skillById(id))

function allSlotIds(n: Ninja): string[] {
  const ids: string[] = []
  const push = (slots: SkillSlot[]) => {
    for (const s of slots) {
      ids.push(s.initial)
      for (const id of s.breakList || []) ids.push(id)
      if (s.y) ids.push(s.y)
      if (s.l) ids.push(s.l)
    }
  }
  for (const f of n.forms) push(f.slots)
  if (n.transform) push(n.transform.slots)
  return ids
}

function matchesKeyword(n: Ninja, kw: string): boolean {
  if (!kw) return true
  if (n.name.toLowerCase().includes(kw)) return true
  return allSlotIds(n).some(id => {
    const s = skills.value[id]
    return !!s && ((s.name || '').toLowerCase().includes(kw) || stripTags(s.desc).toLowerCase().includes(kw))
  })
}

const filtered = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const list = all.value.filter(n => {
    if (!matchesKeyword(n, kw)) return false
    if (rare.value !== '' && rare.value != null && n.rare !== rare.value) return false
    if (star.value !== '' && star.value != null && n.star !== star.value) return false
    if (attributeFilter.value && n.attribute !== attributeFilter.value) return false
    if (officialTag.value && !(n.tags || []).includes(officialTag.value)) return false
    if (opTag.value === 'transform' && !n.flags.transform) return false
    if (opTag.value === 'season' && !(n.tags || []).includes('赛季')) return false
    if (breakFilter.value === 'yes' && !n.flags.break) return false
    if (breakFilter.value === 'none' && n.flags.break) return false
    if (variantFilter.value && !n.flags[variantFilter.value]) return false
    if (skillKeyword.value) {
      const hit = allSlotIds(n).some(id => stripTags(skills.value[id]?.desc || '').includes(skillKeyword.value))
      if (!hit) return false
    }
    return true
  })

  if (sortBy.value === 'rare') {
    return [...list].sort((a, b) => (b.rare - a.rare) || a.name.localeCompare(b.name, 'zh-Hans-CN'))
  }
  if (sortBy.value === 'star') {
    return [...list].sort((a, b) => (b.star - a.star) || a.name.localeCompare(b.name, 'zh-Hans-CN'))
  }
  if (sortBy.value === 'name') {
    return [...list].sort((a, b) => a.name.localeCompare(b.name, 'zh-Hans-CN'))
  }
  return list
})

const total = computed(() => all.value.length)
const skillTotal = computed(() => Object.keys(skills.value).length)
const formTotal = computed(() => all.value.reduce((sum, n) => sum + n.forms.length + (n.transform ? 1 : 0), 0))

// ===== 分页 =====
const page = ref(1)
const size = ref(24)
const paged = computed(() => {
  const start = (page.value - 1) * size.value
  return filtered.value.slice(start, start + size.value)
})

function resetPage() {
  page.value = 1
}

function onSizeChange() {
  page.value = 1
}

watch([searchKeyword, rare, star, skillKeyword, sortBy, variantFilter, attributeFilter, officialTag, opTag, breakFilter], resetPage)
watch(filtered, () => {
  const maxPage = Math.max(1, Math.ceil(filtered.value.length / size.value))
  if (page.value > maxPage) page.value = maxPage
})

function resetFilters() {
  clearTimeout(searchTimer)
  keyword.value = ''
  searchKeyword.value = ''
  rare.value = ''
  star.value = ''
  skillKeyword.value = ''
  sortBy.value = 'default'
  variantFilter.value = ''
  attributeFilter.value = ''
  officialTag.value = ''
  opTag.value = ''
  breakFilter.value = ''
  page.value = 1
}

function randomNinja() {
  const pool = filtered.value.length ? filtered.value : all.value
  if (!pool.length) return
  openDetail(pool[Math.floor(Math.random() * pool.length)])
}

// ===== 卡片悬停：单列面板，只有指向忍者卡片时才显示 =====
const hoverNinja = ref<Ninja | null>(null)
const hoverX = ref(0)
const hoverY = ref(0)
/** 图标 404 时回退到角色色块，避免出现裂图 */
const failedIcons = ref<Set<string>>(new Set())

function onIconError(icon: string) {
  if (!icon || failedIcons.value.has(icon)) return
  const next = new Set(failedIcons.value)
  next.add(icon)
  failedIcons.value = next
}

/** 当前可用的技能图标：主图标 → 兜底链 → 空（显示角色色块） */
function activeIcon(s: NinjaSkill | null): string {
  if (!s) return ''
  for (const path of [s.icon, ...(s.iconFallback || [])]) {
    if (path && !failedIcons.value.has(path)) return path
  }
  return ''
}

function hideHoverNow() {
  hoverNinja.value = null
}

function onHoverEnter(n: Ninja, e: MouseEvent) {
  hoverNinja.value = n
  hoverX.value = e.clientX
  hoverY.value = e.clientY
}

function onHoverMove(e: MouseEvent) {
  if (!hoverNinja.value) return
  hoverX.value = e.clientX
  hoverY.value = e.clientY
}

const hoverMainSkillsAll = computed<NinjaSkill[]>(() => {
  const n = hoverNinja.value
  if (!n) return []
  return (n.forms[0]?.slots || [])
    .map(s => slotDisplaySkill(s))
    .filter((s): s is NinjaSkill => !!s)
})

const hoverTransformSkillsAll = computed<NinjaSkill[]>(() => {
  const n = hoverNinja.value
  if (!n?.transform) return []
  return n.transform.slots
    .map(s => slotDisplaySkill(s))
    .filter((s): s is NinjaSkill => !!s)
})

const hoverMainSkills = computed(() => hoverMainSkillsAll.value)
const hoverTransformSkills = computed(() => hoverTransformSkillsAll.value)
const hoverStatuses = computed(() => hoverStatusesAll.value)

const hoverStatusesAll = computed<SkillState[]>(() => {
  const out: SkillState[] = []
  const seen = new Set<string>()
  for (const s of [...hoverMainSkillsAll.value, ...hoverTransformSkillsAll.value]) {
    for (const st of s.states || []) {
      if (seen.has(st.name)) continue
      seen.add(st.name)
      out.push(st)
    }
  }
  return out
})

const HOVER_GAP = 8
const HOVER_PAD = 12
const HOVER_MAIN_W = 430
const HOVER_TRANSFORM_W = 430
const HOVER_STATUS_W = 250

const hoverClamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(value, Math.max(min, max)))

/** 估算技能面板高度：描述按 430px 宽度约 24 字一行，最多按 8 行计，超出由 CSS 滚动兜底 */
function hoverSkillPanelHeight(skills: NinjaSkill[], header: number): number {
  let height = header
  for (const skill of skills) {
    const lines = Math.max(1, Math.ceil(stripTags(skill.desc || '').length / 24))
    height += 44 + Math.min(lines, 8) * 16
  }
  return height
}

function hoverStatusPanelHeight(statuses: SkillState[], header: number): number {
  let height = header
  for (const state of statuses) {
    const lines = Math.max(1, Math.ceil(stripTags(state.desc || '').length / 16))
    height += 30 + Math.min(lines, 5) * 16
  }
  return height
}

const hoverLayout = computed(() => {
  const vw = window.innerWidth
  const vh = window.innerHeight
  const x = hoverX.value
  const y = hoverY.value
  const pad = HOVER_PAD
  const gap = HOVER_GAP
  const maxH = vh - pad * 2

  const hasStatus = hoverStatuses.value.length > 0
  const hasTransform = hoverTransformSkills.value.length > 0

  const mainW = Math.min(HOVER_MAIN_W, vw - pad * 2)
  const transformW = Math.min(HOVER_TRANSFORM_W, vw - pad * 2)
  const statusW = Math.min(HOVER_STATUS_W, vw - pad * 2)
  const mainH = hoverSkillPanelHeight(hoverMainSkills.value, 58)
  const transformH = hasTransform ? hoverSkillPanelHeight(hoverTransformSkills.value, 30) : 0
  const statusH = hasStatus ? hoverStatusPanelHeight(hoverStatuses.value, 30) : 0

  const style = (left: number, top: number, width: number, maxHeight: number) => ({
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    maxHeight: `${Math.max(120, maxHeight)}px`,
  })

  // 1) 三列横排：状态 | 主技能 | 变身后技能。整排一起移动，天然不会互相重叠。
  const rowW = (hasStatus ? statusW + gap : 0) + mainW + (hasTransform ? gap + transformW : 0)
  if (rowW <= vw - pad * 2) {
    const rowH = Math.max(mainH, hasTransform ? transformH : 0, hasStatus ? statusH : 0)
    let left = x + gap
    if (left + rowW > vw - pad) left = x - rowW - gap
    left = hoverClamp(left, pad, vw - rowW - pad)
    const top = hoverClamp(y - rowH / 2, pad, vh - rowH - pad)
    const statusLeft = left
    const mainLeft = left + (hasStatus ? statusW + gap : 0)
    const transformLeft = mainLeft + mainW + gap
    return {
      status: style(statusLeft, top, statusW, maxH),
      main: style(mainLeft, top, mainW, maxH),
      transform: style(transformLeft, top, transformW, maxH),
    }
  }

  // 2) 两列：主技能 | 变身后技能，状态面板放在这一组下方或上方。
  const pairW = mainW + (hasTransform ? gap + transformW : 0)
  const pairH = Math.max(mainH, hasTransform ? transformH : 0)
  const totalH = pairH + (hasStatus ? gap + statusH : 0)
  if (pairW <= vw - pad * 2 && totalH <= maxH) {
    let left = x + gap
    if (left + pairW > vw - pad) left = x - pairW - gap
    left = hoverClamp(left, pad, vw - pairW - pad)
    const top = hoverClamp(y - totalH / 2, pad, vh - totalH - pad)
    const mainLeft = left
    const transformLeft = left + mainW + gap
    const statusTop = top + pairH + gap
    return {
      status: style(left, statusTop, statusW, maxH),
      main: style(mainLeft, top, mainW, maxH),
      transform: style(transformLeft, top, transformW, maxH),
    }
  }

  // 3) 竖排兜底：状态 → 主技能 → 变身后技能，按可用高度等比压缩，超出由面板内部滚动。
  const stackW = Math.min(Math.max(mainW, transformW, statusW), vw - pad * 2)
  let left = x + 18
  if (left + stackW > vw - pad) left = x - stackW - gap
  left = hoverClamp(left, pad, vw - stackW - pad)
  const gaps = (hasStatus ? 1 : 0) + (hasTransform ? 1 : 0)
  const avail = vh - pad * 2 - gap * gaps
  const rawTotal = mainH + (hasStatus ? statusH : 0) + (hasTransform ? transformH : 0)
  const scale = rawTotal > avail ? avail / rawTotal : 1
  const mainMax = mainH * scale
  const transformMax = transformH * scale
  const statusMax = statusH * scale
  const total = mainMax + (hasStatus ? statusMax : 0) + (hasTransform ? transformMax : 0)
  let top = hoverClamp(y - 40, pad, vh - total - pad)
  const statusTop = top
  top += (hasStatus ? statusMax + gap : 0)
  const mainTop = top
  top += mainMax + gap
  const transformTop = top
  return {
    status: style(left, statusTop, statusW, statusMax),
    main: style(left, mainTop, mainW, mainMax),
    transform: style(left, transformTop, transformW, transformMax),
  }
})

const hoverMainStyle = computed(() => hoverLayout.value.main)
const hoverStatusStyle = computed(() => hoverLayout.value.status)
const hoverTransformStyle = computed(() => hoverLayout.value.transform)

function hasVariantFlags(n: Ninja): boolean {
  return !!(n.flags.star || n.flags.break || n.flags.y || n.flags.l || n.flags.transform)
}

// ===== 详情 =====
const detail = ref<Ninja | null>(null)
const detailVisible = ref(false)
const activeFormIndex = ref(0)

/** 星级形态 + 变身后，统一成可切换的 tab */
const allForms = computed<NinjaForm[]>(() => {
  const n = detail.value
  if (!n) return []
  const forms = [...(n.forms || [])]
  if (n.transform) {
    forms.push({
      key: 'extra',
      label: n.transform.label || '变身后',
      star: n.star,
      kind: 'extra',
      slots: n.transform.slots,
    })
  }
  return forms
})

const activeForm = computed<NinjaForm | null>(() => {
  return allForms.value[activeFormIndex.value] || allForms.value[0] || null
})

const activeFormIsExtra = computed(() => activeForm.value?.kind === 'extra')
const tableRows = computed<SkillSlot[]>(() => activeForm.value?.slots || [])

const breakColumns = computed(() => {
  let max = 0
  for (const slot of tableRows.value) {
    max = Math.max(max, slot.breakList?.length || 0)
  }
  return Math.min(max, 4)
})

const hasYColumn = computed(() => tableRows.value.some(slot => !!slot.y))
const hasLColumn = computed(() => tableRows.value.some(slot => !!slot.l))

/** 第 i 个突破（1 起）对应的技能 id，没有则返回空串 */
function breakIdAt(slot: SkillSlot, i: number): string {
  return slot.breakList?.[i - 1] || ''
}

function openDetail(n: Ninja) {
  hoverNinja.value = null
  detail.value = n
  activeFormIndex.value = 0
  detailVisible.value = true
}
</script>

<style scoped lang="scss">
.ninja-guide {
  .codex-hero {
    position: relative;
    overflow: hidden;
    padding: 16px 20px 15px;
    border-radius: 16px;
    border: 1px solid var(--ev-border-subtle);
    background:
      radial-gradient(120% 180% at 92% 0%, rgba(var(--ev-primary-rgb), 0.16), transparent 55%),
      radial-gradient(80% 140% at 4% 100%, rgba(var(--ev-aqua-rgb), 0.1), transparent 60%),
      var(--ev-bg-surface);
    box-shadow: var(--ev-shadow-sm);
  }

  .hero-content {
    position: relative;
    z-index: 1;
  }

  .hero-top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 10px 18px;
  }

  .hero-heading {
    display: flex;
    align-items: baseline;
    gap: 12px;
    min-width: 0;
  }

  .hero-kicker {
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.16em;
    color: var(--ev-primary);
    white-space: nowrap;
  }

  h1 {
    margin: 0;
    font-size: 24px;
    line-height: 1.15;
    font-weight: 800;
    letter-spacing: 0.02em;
    background: linear-gradient(120deg, var(--ev-primary), var(--ev-aqua));
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }

  p {
    margin: 5px 0 0;
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  .hero-stats {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .stat {
    display: inline-flex;
    align-items: baseline;
    gap: 5px;
    padding: 4px 10px;
    border-radius: 999px;
    background: rgba(var(--ev-primary-rgb), 0.07);
    border: 1px solid rgba(var(--ev-primary-rgb), 0.12);

    strong {
      font-size: 15px;
      line-height: 1.3;
      font-variant-numeric: tabular-nums;
      color: var(--ev-text-primary);
    }

    span {
      font-size: 10px;
      color: var(--ev-text-muted);
    }
  }

  .hero-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .import-progress {
    font-size: 11px;
    font-variant-numeric: tabular-nums;
    color: var(--ev-primary);
    font-weight: 700;
  }

  .folder-input {
    display: none;
  }

  .hero-mark {
    position: absolute;
    right: 10px;
    bottom: -34px;
    font-size: 148px;
    font-weight: 900;
    line-height: 1;
    font-family: "STKaiti", "KaiTi", "Songti SC", serif;
    color: rgba(var(--ev-primary-rgb), 0.045);
    pointer-events: none;
    user-select: none;
  }

  .codex-toolbar {
    position: sticky;
    top: 8px;
    z-index: 20;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin: 12px 0;
    padding: 8px 10px;
    border-radius: 12px;
    border: 1px solid var(--ev-border-subtle);
    background: var(--ev-bg-surface);
    box-shadow: var(--ev-shadow-sm);

    .tool-search { flex: 1; min-width: 190px; }
    .tool-select { width: 102px; }
    .tool-official { width: 132px; }
    .tool-keyword { width: 124px; }
    .tool-sort { width: 118px; }
    .tool-variant { width: 120px; }
    .tool-reset,
    .tool-random { flex-shrink: 0; }
  }

  .codex-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
    gap: 22px 16px;
    min-height: 320px;
  }

  .ninja-card {
    cursor: pointer;
    outline: none;
    transition: transform 0.28s var(--ev-ease-out);

    &:hover,
    &:focus-visible {
      transform: translateY(-6px);

      .portrait {
        border-color: rgba(var(--ev-primary-rgb), 0.5);
        box-shadow: 0 18px 34px rgba(0, 0, 0, 0.18);
      }

      .portrait img {
        transform: scale(1.045);
      }

      .portrait-hover {
        opacity: 1;
        transform: none;
      }
    }

    &:focus-visible .portrait {
      outline: 2px solid var(--ev-primary);
      outline-offset: 2px;
    }
  }

  .portrait {
    position: relative;
    aspect-ratio: 300 / 410;
    overflow: hidden;
    border-radius: 16px;
    border: 1px solid var(--ev-border-subtle);
    background:
      radial-gradient(90% 72% at 50% 100%, rgba(var(--ev-primary-rgb), 0.24), transparent 66%),
      linear-gradient(160deg, rgba(var(--ev-primary-rgb), 0.12), rgba(var(--ev-aqua-rgb), 0.05) 60%, transparent);
    transition: border-color 0.28s, box-shadow 0.28s;

    .aura {
      position: absolute;
      left: 50%;
      bottom: -20%;
      width: 82%;
      height: 56%;
      transform: translateX(-50%);
      background: radial-gradient(closest-side, rgba(var(--ev-primary-rgb), 0.36), transparent 72%);
      filter: blur(18px);
      pointer-events: none;
    }

    img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      padding: 10px 8px 0;
      object-fit: contain;
      filter: drop-shadow(0 14px 18px rgba(0, 0, 0, 0.24));
      transition: transform 0.35s var(--ev-ease-out);
    }
  }

  .portrait-fallback {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 56px;
    font-weight: 800;
    color: rgba(var(--ev-primary-rgb), 0.45);
  }

  .portrait-top {
    position: absolute;
    top: 8px;
    left: 8px;
    right: 8px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    pointer-events: none;

    .rare-tag,
    .star-tag {
      font-size: 10px;
      line-height: 18px;
      padding: 0 7px;
      border-radius: 999px;
      color: #fff;
      background: rgba(0, 0, 0, 0.42);
      letter-spacing: 0.02em;
    }

    .rare-tag.rare-2 { background: rgba(74, 144, 226, 0.9); }
    .rare-tag.rare-3 { background: rgba(155, 89, 182, 0.9); }
    .rare-tag.rare-4 { background: rgba(230, 162, 60, 0.92); }

    .star-tag {
      color: #ffd166;
      letter-spacing: 1px;
    }
  }

  .variant-flags {
    position: absolute;
    left: 8px;
    bottom: 8px;
    display: flex;
    gap: 4px;
    z-index: 2;
    pointer-events: none;

    .vf {
      font-size: 10px;
      line-height: 17px;
      padding: 0 6px;
      border-radius: 6px;
      font-weight: 700;
      color: #fff;
      background: rgba(0, 0, 0, 0.48);

      &.star { color: #ffd166; }
      &.break { color: #7fb2ff; }
      &.y { color: #ffb066; }
      &.l { color: #86e2c4; }
      &.transform { color: #9fe3a6; }
    }
  }

  .portrait-hover {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 34px 10px 10px;
    background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
    opacity: 0;
    transform: translateY(6px);
    transition: opacity 0.25s, transform 0.25s;
  }

  .portrait-hover .hint {
    display: block;
    font-size: 10.5px;
    color: #ffd166;
    text-align: right;
  }

  .card-name {
    margin-top: 9px;
    text-align: center;
    font-size: 13.5px;
    font-weight: 600;
    color: var(--ev-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .grid-state { grid-column: 1 / -1; }

  .list-footer {
    margin-top: 24px;
    display: flex;
    justify-content: center;
  }

  .codex-foot {
    margin-top: 18px;
    text-align: center;
    font-size: 11px;
    color: var(--ev-text-muted);
  }

  .codex-stale {
    color: var(--el-color-warning);
  }

  @media (max-width: 900px) {
    .codex-hero { padding: 14px 14px 13px; }
    .hero-top { align-items: flex-start; }
    .hero-heading { flex-direction: column; gap: 2px; }
    h1 { font-size: 21px; }
    p { font-size: 11px; }
    .hero-mark { font-size: 116px; right: -2px; bottom: -24px; }

    .codex-toolbar {
      .tool-search { flex-basis: 100%; }
      .tool-select,
      .tool-official,
      .tool-keyword,
      .tool-sort,
      .tool-variant {
        flex: 1;
        width: auto;
        min-width: 100px;
      }
    }

    .codex-grid {
      grid-template-columns: repeat(auto-fill, minmax(142px, 1fr));
      gap: 18px 12px;
    }
  }
}
</style>

<style lang="scss">
.ninja-hover-layer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  pointer-events: none;
}

.hover-panel {
  position: fixed;
  pointer-events: none;
  max-height: calc(100vh - 24px);
  border: 1px solid var(--ev-border-default);
  border-radius: 12px;
  background: var(--ev-bg-dialog);
  color: var(--ev-text-primary);
  box-shadow: var(--ev-shadow-float);
  overflow-x: hidden;
  overflow-y: auto;
  backdrop-filter: blur(10px);
}

.hp-title {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px 6px;
  border-bottom: 1px solid var(--ev-border-subtle);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--ev-primary);
}

.hp-badge {
  font-size: 9px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-color-success);
  color: var(--ev-text-on-accent);
  font-weight: 700;
}

.hover-main .hp-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px 8px;
  background: linear-gradient(120deg, var(--ev-bg-tint-strong), transparent 70%);
  border-bottom: 1px solid var(--ev-border-subtle);
}

.hp-avatar {
  width: 62px;
  height: 46px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 8px;
  background: var(--ev-bg-tint);
  border: 1px solid var(--ev-border-subtle);
  font-size: 22px;
  font-weight: 800;
  color: var(--ev-primary);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: left center;
    display: block;
  }
}

.hp-meta {
  min-width: 0;
}

.hp-ninja-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--ev-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hp-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--ev-text-muted);

  .hp-stars { color: var(--el-color-warning); letter-spacing: 1px; }
  .hp-transform { color: var(--el-color-success); font-weight: 700; }
}

.hp-skills {
  padding: 4px 13px 7px;
  overflow: hidden;
}

.hp-skill {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--ev-border-default);

  &:last-child { border-bottom: 0; }
}

.hp-role {
  flex-shrink: 0;
  width: 16px;
  padding: 3px 0;
  border-radius: 5px;
  font-size: 9px;
  line-height: 1.25;
  font-weight: 700;
  text-align: center;
  color: var(--ev-text-on-accent);
  writing-mode: vertical-rl;
  letter-spacing: 0.18em;
  background: var(--ev-text-muted);

  &.ultimate { background: var(--ev-primary); }
  &.normal { background: #4aa8a0; }
  &.chase { background: #e6a23c; }
  &.trigger { background: #0ea5a4; }
  &.passive { background: #8a94a6; }
  &.other { background: var(--ev-text-muted); }
}

.hp-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 7px;
  background: var(--ev-bg-tint);
  border: 1px solid var(--ev-border-subtle);
  color: var(--ev-text-on-accent);
  font-size: 12px;
  font-weight: 700;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
  }

  &.ultimate { background: rgba(var(--ev-primary-rgb), 0.78); }
  &.normal { background: rgba(74, 168, 160, 0.78); }
  &.chase { background: rgba(230, 162, 60, 0.82); }
  &.trigger { background: rgba(14, 165, 164, 0.78); }
  &.passive { background: rgba(138, 148, 166, 0.78); }
}

.hp-body {
  min-width: 0;
  flex: 1;
}

.hp-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.hp-name {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--ev-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hp-stats {
  margin-top: 2px;
  font-size: 9.5px;
  line-height: 1.35;
  color: var(--ev-text-muted);
}

.hp-desc {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ev-text-secondary);
  white-space: normal;
  word-break: break-word;

  font { font-weight: 600; }
}

.hover-status .hp-status-list {
  padding: 5px 13px 9px;
  overflow: hidden;
}

.hp-status {
  padding: 7px 0;
  border-bottom: 1px dashed var(--ev-border-default);

  &:last-child { border-bottom: 0; }
}

.hp-status-name {
  display: block;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ev-primary);
}

.hp-status-desc {
  display: block;
  margin-top: 2px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ev-text-secondary);
  white-space: normal;
  word-break: break-word;
}

.hp-empty {
  padding: 12px 13px;
  font-size: 11px;
  color: var(--ev-text-muted);
}

.ninja-codex-dialog {
  .el-dialog__body {
    padding-top: 4px;
  }

  .detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .detail-title-wrap {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }

  .detail-head-avatar {
    width: 72px;
    height: 52px;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    border-radius: 9px;
    border: 1px solid var(--ev-border-subtle);
    background: var(--ev-bg-tint);
    color: var(--ev-primary);
    font-size: 22px;
    font-weight: 800;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: left center;
      display: block;
    }
  }

  .detail-title-text {
    min-width: 0;
  }

  .detail-name {
    font-size: 20px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .detail-sub {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 4px;
    font-size: 12px;
    color: var(--ev-text-muted);

    .detail-stars { color: #f2b13c; letter-spacing: 1px; }
    .detail-transform { color: #2fa36b; font-weight: 700; }
    .detail-break { color: var(--el-color-warning); font-weight: 700; }
  }

  .form-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .form-tab {
    padding: 4px 12px;
    border: 1px solid var(--ev-border-subtle);
    border-radius: 8px;
    background: var(--ev-bg-tint);
    color: var(--ev-text-secondary);
    font-size: 12px;
    font-weight: 600;
    line-height: 20px;
    cursor: pointer;
    transition: border-color 0.18s, color 0.18s, background 0.18s;

    &:hover {
      border-color: rgba(var(--ev-primary-rgb), 0.45);
      color: var(--ev-primary);
    }

    &.active {
      border-color: var(--ev-primary);
      background: rgba(var(--ev-primary-rgb), 0.1);
      color: var(--ev-primary);
    }
  }

  .detail-body {
    display: grid;
    grid-template-columns: 300px minmax(0, 1fr);
    gap: 22px;
    align-items: start;
    max-height: 76vh;
    overflow-y: auto;
    padding-right: 6px;
  }

  .detail-left {
    display: flex;
    flex-direction: column;
    gap: 14px;
    min-width: 0;
  }

  .detail-portrait {
    position: relative;
    width: 100%;
    aspect-ratio: 300 / 410;
    overflow: hidden;
    border-radius: 16px;
    border: 1px solid var(--ev-border-subtle);
    background:
      radial-gradient(90% 72% at 50% 100%, rgba(var(--ev-primary-rgb), 0.24), transparent 66%),
      linear-gradient(160deg, rgba(var(--ev-primary-rgb), 0.12), rgba(var(--ev-aqua-rgb), 0.05) 60%, transparent);

    .aura {
      position: absolute;
      left: 50%;
      bottom: -20%;
      width: 82%;
      height: 56%;
      transform: translateX(-50%);
      background: radial-gradient(closest-side, rgba(var(--ev-primary-rgb), 0.36), transparent 72%);
      filter: blur(18px);
      pointer-events: none;
    }

    img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      padding: 12px 10px 0;
      object-fit: contain;
      filter: drop-shadow(0 14px 18px rgba(0, 0, 0, 0.24));
    }

    .portrait-fallback {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 64px;
      font-weight: 800;
      color: rgba(var(--ev-primary-rgb), 0.45);
    }
  }

  .break-block {
    padding: 13px 14px 14px;
    border: 1px solid var(--ev-border-subtle);
    border-radius: 14px;
    background: var(--ev-bg-surface);
  }

  .panel-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .break-hint {
    margin-left: auto;
    font-size: 10.5px;
    font-weight: 400;
    color: var(--ev-text-muted);
  }

  .break-condition {
    font-size: 10px;
    line-height: 17px;
    padding: 0 7px;
    border-radius: 5px;
    color: var(--ev-primary);
    background: var(--ev-bg-tint-strong);
    font-weight: 700;
  }

  .detail-right {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .extra-skill-list {
    margin-top: 10px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .extra-skill {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 9px 10px;
    border-radius: 10px;
    background: var(--ev-bg-tint);
  }

  .extra-icon {
    width: 42px;
    height: 42px;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    border-radius: 8px;
    border: 1px solid var(--ev-border-subtle);
    background: var(--ev-bg-surface);
    color: var(--ev-text-on-accent);
    font-size: 13px;
    font-weight: 700;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
      display: block;
    }
  }

  .extra-body {
    min-width: 0;
    flex: 1;
  }

  .extra-name {
    font-size: 13px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .extra-stats {
    margin-top: 2px;
    font-size: 10.5px;
    color: var(--ev-text-muted);
  }

  .extra-desc {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--ev-text-secondary);
    word-break: break-word;

    font { font-weight: 600; }
  }

  .break-scroll {
    margin-top: 10px;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .break-table {
    width: 100%;
    min-width: 620px;
    border-collapse: separate;
    border-spacing: 0 6px;
    font-size: 12px;

    thead th {
      padding: 0 8px 2px;
      font-size: 10.5px;
      font-weight: 700;
      color: var(--ev-text-muted);
      text-align: center;
      white-space: nowrap;
    }

    tbody tr {
      background: var(--ev-bg-tint);
    }

    tbody td {
      padding: 6px 8px;
      vertical-align: middle;
    }

    tbody td:first-child {
      border-radius: 10px 0 0 10px;
    }

    tbody td:last-child {
      border-radius: 0 10px 10px 0;
    }
  }

  .role-col {
    width: 64px;
    text-align: center;
  }

  .role-cell {
    display: inline-block;
    min-width: 38px;
    padding: 2px 6px;
    font-size: 11.5px;
    font-weight: 700;
    line-height: 18px;
    text-align: center;
    border-radius: 6px;
    color: #fff;
    background: var(--ev-text-muted);

    &.ultimate { background: var(--ev-primary); }
    &.normal { background: #4aa8a0; }
    &.chase { background: #e6a23c; }
    &.trigger { background: #0ea5a4; }
    &.passive { background: #8a94a6; }
    &.other { background: var(--ev-text-muted); }
  }

  .cell {
    text-align: center;
    min-width: 0;
  }

  .initial-cell {
    position: relative;
  }

  .chosen {
    display: block;
    margin-top: 2px;
    font-size: 9px;
    font-weight: 700;
    color: var(--ev-primary);
  }

  .cell-empty {
    font-size: 13px;
    color: var(--ev-text-placeholder, #c0c4cc);
  }

  @media (max-width: 860px) {
    width: 94% !important;

    .detail-body {
      grid-template-columns: 1fr;
      gap: 16px;
      max-height: 74vh;
    }

    .detail-portrait {
      width: 210px;
      margin: 0 auto;
    }
  }
}
</style>
