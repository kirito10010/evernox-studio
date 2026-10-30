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
        <el-empty description="忍者数据加载失败">
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
          @mouseleave="onHoverLeave"
        >
          <div class="portrait">
            <span class="aura" aria-hidden="true"></span>
            <img
              v-if="!n.portraitFailed"
              :src="asset(n.portrait)"
              :alt="n.name"
              loading="lazy"
              @error="n.portraitFailed = true"
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
      数据来源：火影忍者OL 客户端配置（NinjaInfo / NinjaSkill / SkillCFG / SkillExState / SkillKeyDesc）· 已离线内置
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
              <span class="hp-icon" :class="typeClass(s.type)">
                <img v-if="s.icon" :src="asset(s.icon)" alt="" />
                <span v-else>{{ typeShort(s.type) }}</span>
              </span>
              <div class="hp-body">
                <div class="hp-name-row">
                  <span class="hp-type" :class="typeClass(s.type)">{{ s.type }}</span>
                  <span class="hp-name">{{ s.name || s.type }}</span>
                </div>
                <div class="hp-stats">冷却回合:{{ s.cd }} · 进场冷却:{{ s.enterCd }} · 消耗查克拉:{{ s.mp }}</div>
                <div class="hp-desc" v-html="renderDesc(s.desc)"></div>
              </div>
            </div>
          </div>
          <div v-if="hoverTransformMore" class="hp-more">还有 {{ hoverTransformMore }} 个技能，点击卡片查看</div>
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
          <div v-if="hoverStatusMore" class="hp-more">还有 {{ hoverStatusMore }} 条状态，点击卡片查看</div>
        </section>

        <section class="hover-panel hover-main" :style="hoverMainStyle">
          <div class="hp-head">
            <span class="hp-avatar">
              <img v-if="hoverNinja.head" :src="asset(hoverNinja.head)" alt="" />
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
              <span class="hp-icon" :class="typeClass(s.type)">
                <img v-if="s.icon" :src="asset(s.icon)" alt="" />
                <span v-else>{{ typeShort(s.type) }}</span>
              </span>
              <div class="hp-body">
                <div class="hp-name-row">
                  <span class="hp-type" :class="typeClass(s.type)">{{ s.type }}</span>
                  <span class="hp-name">{{ s.name || s.type }}</span>
                </div>
                <div class="hp-stats">冷却回合:{{ s.cd }} · 进场冷却:{{ s.enterCd }} · 消耗查克拉:{{ s.mp }}</div>
                <div class="hp-desc" v-html="renderDesc(s.desc)"></div>
              </div>
            </div>
          </div>
          <div v-if="hoverMainMore" class="hp-more">还有 {{ hoverMainMore }} 个技能，点击卡片查看</div>
          <div class="hp-foot">点击卡片查看技能突破表</div>
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
                <span v-if="detail.forms.length > 1">{{ detail.forms.length }} 个星级形态</span>
                <span v-if="detail.flags.break && detail.breakthroughStar > 0" class="detail-break">突破条件：{{ detail.breakthroughStar }}星</span>
                <span v-if="detail.transform" class="detail-transform">可变身</span>
              </div>
            </div>
          </div>
          <div v-if="detail.forms.length > 1" class="star-select">
            <span class="star-select-label">星级</span>
            <el-select v-model="activeFormIndex" size="small" class="star-select-input">
              <el-option
                v-for="(f, i) in detail.forms"
                :key="f.key"
                :label="f.label"
                :value="i"
              />
            </el-select>
          </div>
        </div>
      </template>

      <div v-if="detail" class="detail-body">
        <aside class="detail-left">
          <div class="detail-portrait">
            <span class="aura" aria-hidden="true"></span>
            <img
              v-if="!detail.portraitFailed"
              :src="asset(detail.portrait)"
              :alt="detail.name"
              @error="detail.portraitFailed = true"
            />
            <span v-else class="portrait-fallback">{{ detail.name.slice(0, 1) }}</span>
          </div>
        </aside>

        <main class="detail-right">
          <section v-if="detail.transform" class="transform-block">
            <div class="panel-title">
              变身后技能
              <span class="transform-badge">变身</span>
            </div>
            <div class="transform-slots">
              <div
                v-for="(slot, i) in transformSlots"
                :key="`${slot.role}-${slot.initial}-${i}`"
                class="transform-slot"
              >
                <SkillIcon
                  :skill="slotSkill(slot)"
                  :icon-url="slotIcon(slot)"
                  size="md"
                />
                <span class="transform-role">{{ slot.role }}</span>
              </div>
            </div>
          </section>

          <section class="break-block">
            <div class="panel-title">
              技能突破
              <span v-if="detail.flags.break && detail.breakthroughStar > 0" class="break-condition">{{ detail.breakthroughStar }}星解锁</span>
              <span class="break-hint">悬停图标查看技能详情</span>
            </div>

            <div class="break-scroll">
              <div class="break-table">
                <div class="break-row break-head">
                  <span class="role-cell">技能</span>
                  <span>初始</span>
                  <span>突破</span>
                  <span>变异 Y</span>
                  <span>链接 L</span>
                </div>

                <div
                  v-for="(slot, idx) in tableRows"
                  :key="`${slot.role}-${slot.initial}-${idx}`"
                  class="break-row"
                >
                  <div class="role-cell" :class="typeClass(slot.role)">
                    <span>{{ slot.role }}</span>
                  </div>

                  <div class="cell initial-cell">
                    <SkillIcon
                      :skill="slotSkill(slot)"
                      :icon-url="slotIcon(slot)"
                      size="md"
                    />
                  </div>

                  <div class="cell multi-cell">
                    <template v-if="hasBreakthrough(slot)">
                      <SkillIcon
                        v-for="bt in breakthroughRefs(slot)"
                        :key="bt.id"
                        :skill="variantSkill(bt.id)"
                        :icon-url="slotIcon(slot)"
                        :base-skill="slotSkill(slot)"
                        :badge="bt.label"
                        size="sm"
                      />
                    </template>
                    <span v-else class="cell-empty">—</span>
                  </div>

                  <div class="cell">
                    <SkillIcon
                      v-if="slot.y"
                      :skill="variantSkill(slot.y)"
                      :icon-url="variantIcon(slot.y)"
                      :base-skill="slotSkill(slot)"
                      badge="Y"
                      size="sm"
                    />
                    <span v-else class="cell-empty">—</span>
                  </div>

                  <div class="cell">
                    <SkillIcon
                      v-if="slot.l"
                      :skill="variantSkill(slot.l)"
                      :icon-url="variantIcon(slot.l)"
                      :base-skill="slotSkill(slot)"
                      badge="L"
                      size="sm"
                    />
                    <span v-else class="cell-empty">—</span>
                  </div>
                </div>
              </div>
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
import SkillIcon from '@/components/ninja/SkillIcon.vue'

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
  p1?: string
  p2?: string
  p3?: string
  p4?: string
  y?: string
  l?: string
  /** 同一被动/技能被拆成“基础 + 此外…”时的附加效果，展示时合并到初始技能 */
  extraSkills?: SkillExtra[]
}

interface NinjaForm {
  key: string
  label: string
  star: number
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
  flags: NinjaFlags
  forms: NinjaForm[]
  transform: NinjaTransform | null
  portraitFailed?: boolean
}

interface CodexData {
  version: number
  skills: Record<string, NinjaSkill>
  ninjas: Ninja[]
}

const BASE = import.meta.env.BASE_URL.endsWith('/')
  ? import.meta.env.BASE_URL
  : `${import.meta.env.BASE_URL}/`

// 数据集结构变更后递增，避免浏览器或 Nginx 继续使用旧缓存
const ASSET_VERSION = '8'
const asset = (p: string) => (p ? `${BASE}ninja/${p}?v=${ASSET_VERSION}` : '')

const SKILL_KEYWORDS = [
  '点燃', '中毒', '混乱', '定身', '麻痹', '封穴', '标记', '睡眠', '目盲', '减速',
  '打断', '击退', '倒地', '小浮空', '大浮空', '高连击', '护盾', '结界',
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

// ===== 数据加载（模块级缓存，切换页面不重复请求） =====
const all = ref<Ninja[]>([])
const skills = ref<Record<string, NinjaSkill>>({})
const loading = ref(false)
const error = ref(false)
let loadPromise: Promise<Ninja[]> | null = null

async function fetchNinjas(): Promise<Ninja[]> {
  const res = await fetch(`${BASE}ninja/ninjas.json?v=${ASSET_VERSION}`, { cache: 'no-cache' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = (await res.json()) as CodexData
  if (!data || !Array.isArray(data.ninjas) || !data.skills) throw new Error('数据格式错误')
  skills.value = data.skills
  return data.ninjas
}

async function load() {
  loading.value = true
  error.value = false
  try {
    if (!loadPromise) loadPromise = fetchNinjas()
    all.value = await loadPromise
  } catch {
    error.value = true
    loadPromise = null
  } finally {
    loading.value = false
  }
}

const route = useRoute()

onMounted(async () => {
  await load()
  const id = String(route.query.ninja || '')
  if (id) {
    const n = all.value.find(x => x.id === id)
    if (n) openDetail(n)
  }
  window.addEventListener('scroll', onHoverLeave, true)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onHoverLeave, true)
})

// ===== 筛选与排序 =====
const keyword = ref('')
const searchKeyword = ref('')
const rare = ref<number | ''>('')
const star = ref<number | ''>('')
const skillKeyword = ref('')
const sortBy = ref<'default' | 'rare' | 'star' | 'name'>('default')
const variantFilter = ref<'' | 'break' | 'y' | 'l' | 'transform' | 'star'>('')

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, v => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = v
  }, 250)
})

const skillById = (id?: string | null): NinjaSkill | null => (id ? skills.value[id] || null : null)
const iconUrl = (s: NinjaSkill | null) => (s?.icon ? asset(s.icon) : '')

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
const variantSkill = (id?: string) => skillById(id)
const variantIcon = (id?: string) => iconUrl(skillById(id))

function allSlotIds(n: Ninja): string[] {
  const ids: string[] = []
  const push = (slots: SkillSlot[]) => {
    for (const s of slots) {
      ids.push(s.initial)
      if (s.p1) ids.push(s.p1)
      if (s.p2) ids.push(s.p2)
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

watch([searchKeyword, rare, star, skillKeyword, sortBy, variantFilter], resetPage)
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
  page.value = 1
}

function randomNinja() {
  const pool = filtered.value.length ? filtered.value : all.value
  if (!pool.length) return
  openDetail(pool[Math.floor(Math.random() * pool.length)])
}

// ===== 卡片悬停：跟随鼠标的 1-3 个面板 =====
const hoverNinja = ref<Ninja | null>(null)
const hoverX = ref(0)
const hoverY = ref(0)

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

const HOVER_MAX_SKILLS = 5
const HOVER_MAX_STATUS = 5

const hoverMainSkills = computed(() => hoverMainSkillsAll.value.slice(0, HOVER_MAX_SKILLS))
const hoverTransformSkills = computed(() => hoverTransformSkillsAll.value.slice(0, HOVER_MAX_SKILLS))
const hoverMainMore = computed(() => Math.max(0, hoverMainSkillsAll.value.length - HOVER_MAX_SKILLS))
const hoverTransformMore = computed(() => Math.max(0, hoverTransformSkillsAll.value.length - HOVER_MAX_SKILLS))

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

const hoverStatuses = computed(() => hoverStatusesAll.value.slice(0, HOVER_MAX_STATUS))
const hoverStatusMore = computed(() => Math.max(0, hoverStatusesAll.value.length - HOVER_MAX_STATUS))

const HOVER_GAP = 18
const HOVER_PAD = 12

const hoverLayout = computed(() => {
  const vw = window.innerWidth
  const vh = window.innerHeight
  const x = hoverX.value
  const y = hoverY.value

  const mainW = 380
  const mainH = Math.min(680, Math.max(220, 78 + hoverMainSkills.value.length * 104 + 28))
  let mainLeft = x + HOVER_GAP
  let mainTop = y + HOVER_GAP
  if (mainLeft + mainW > vw - HOVER_PAD) mainLeft = x - mainW - HOVER_GAP
  mainLeft = Math.max(HOVER_PAD, Math.min(mainLeft, vw - mainW - HOVER_PAD))
  mainTop = Math.max(HOVER_PAD, Math.min(mainTop, vh - mainH - HOVER_PAD))

  const statusW = 248
  const statusH = Math.min(400, Math.max(120, 42 + hoverStatuses.value.length * 56))
  let statusLeft = mainLeft - statusW - 10
  if (statusLeft < HOVER_PAD) statusLeft = mainLeft + mainW + 10
  statusLeft = Math.max(HOVER_PAD, Math.min(statusLeft, vw - statusW - HOVER_PAD))
  const statusTop = Math.max(HOVER_PAD, Math.min(mainTop, vh - statusH - HOVER_PAD))

  const transformW = 380
  const transformH = Math.min(600, Math.max(140, 42 + hoverTransformSkills.value.length * 100))
  let transformTop = mainTop - transformH - 10
  if (transformTop < HOVER_PAD) transformTop = mainTop + mainH + 10
  transformTop = Math.max(HOVER_PAD, Math.min(transformTop, vh - transformH - HOVER_PAD))

  return {
    main: { left: `${mainLeft}px`, top: `${mainTop}px`, width: `${mainW}px` },
    status: { left: `${statusLeft}px`, top: `${statusTop}px`, width: `${statusW}px` },
    transform: { left: `${mainLeft}px`, top: `${transformTop}px`, width: `${transformW}px` },
  }
})

const hoverMainStyle = computed(() => hoverLayout.value.main)
const hoverStatusStyle = computed(() => hoverLayout.value.status)
const hoverTransformStyle = computed(() => hoverLayout.value.transform)

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

function onHoverLeave() {
  hoverNinja.value = null
}

function hasVariantFlags(n: Ninja): boolean {
  return !!(n.flags.star || n.flags.break || n.flags.y || n.flags.l || n.flags.transform)
}

// ===== 详情 =====
const detail = ref<Ninja | null>(null)
const detailVisible = ref(false)
const activeFormIndex = ref(0)

const activeForm = computed<NinjaForm | null>(() => {
  const forms = detail.value?.forms || []
  return forms[activeFormIndex.value] || forms[0] || null
})

const tableRows = computed<SkillSlot[]>(() => activeForm.value?.slots || [])
const transformSlots = computed<SkillSlot[]>(() => detail.value?.transform?.slots || [])

function breakthroughRefs(slot: SkillSlot) {
  return [
    { key: 'p1', label: '+1', id: slot.p1 },
    { key: 'p2', label: '+2', id: slot.p2 },
    { key: 'p3', label: '+3', id: slot.p3 },
    { key: 'p4', label: '+4', id: slot.p4 },
  ].filter((x): x is { key: string; label: string; id: string } => !!x.id)
}

function hasBreakthrough(slot: SkillSlot) {
  return breakthroughRefs(slot).length > 0
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
  overflow: hidden;
  backdrop-filter: blur(10px);
}

.hp-title {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 13px 8px;
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
  padding: 11px 13px 10px;
  background: linear-gradient(120deg, var(--ev-bg-tint-strong), transparent 70%);
  border-bottom: 1px solid var(--ev-border-subtle);
}

.hp-avatar {
  width: 72px;
  height: 52px;
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
  padding: 7px 0;
  border-bottom: 1px dashed var(--ev-border-default);

  &:last-child { border-bottom: 0; }
}

.hp-icon {
  width: 38px;
  height: 38px;
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

.hp-type {
  flex-shrink: 0;
  padding: 0 5px;
  border-radius: 4px;
  font-size: 9px;
  line-height: 15px;
  font-weight: 700;
  color: var(--ev-text-on-accent);

  &.ultimate { background: var(--ev-primary); }
  &.normal { background: #4aa8a0; }
  &.chase { background: #e6a23c; }
  &.trigger { background: #0ea5a4; }
  &.passive { background: #8a94a6; }
  &.other { background: var(--ev-text-muted); }
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
  line-height: 1.55;
  color: var(--ev-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;

  font { font-weight: 600; }
}

.hp-foot {
  padding: 7px 13px 8px;
  border-top: 1px solid var(--ev-border-subtle);
  font-size: 10px;
  color: var(--ev-text-muted);
  text-align: right;
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
  display: -webkit-box;
  margin-top: 2px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ev-text-secondary);
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hp-more {
  padding: 3px 13px 7px;
  font-size: 10px;
  color: var(--ev-text-muted);
  text-align: right;
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

  .star-select {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .star-select-label {
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  .star-select-input { width: 112px; }

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

  .transform-block,
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

  .transform-badge {
    font-size: 10px;
    line-height: 17px;
    padding: 0 7px;
    border-radius: 5px;
    color: #fff;
    background: #10b981;
    font-weight: 700;
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

  .transform-slots {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    margin-top: 11px;
  }

  .transform-slot {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    min-width: 48px;
  }

  .transform-role {
    font-size: 10px;
    color: var(--ev-text-muted);
  }

  .break-scroll {
    margin-top: 10px;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .break-table {
    min-width: 620px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .break-row {
    display: grid;
    grid-template-columns: 60px 118px 170px 78px 78px;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: 10px;
    background: var(--ev-bg-tint);
  }

  .break-head {
    background: transparent;
    padding-top: 0;
    padding-bottom: 2px;
    font-size: 10.5px;
    font-weight: 700;
    color: var(--ev-text-muted);
    text-align: center;

    .role-cell {
      text-align: left;
      background: transparent;
      color: var(--ev-text-muted);
    }
  }

  .role-cell {
    font-size: 11.5px;
    font-weight: 700;
    line-height: 20px;
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
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
  }

  .initial-cell {
    flex-direction: column;
    gap: 4px;
  }

  .multi-cell {
    gap: 6px;
    flex-wrap: wrap;
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
