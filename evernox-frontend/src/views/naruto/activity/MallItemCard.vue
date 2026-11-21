<template>
  <div
    class="item-card"
    :class="{ hoverable: !!detail }"
    @mouseenter="onEnter"
    @mousemove="onMove"
    @mouseleave="onLeave"
  >
    <div class="item-icon">
      <img
        v-if="item.image && !iconFailed"
        :src="imageUrl(item.image)"
        :alt="item.name"
        loading="lazy"
        @error="iconFailed = true"
      />
      <span v-else class="icon-fallback">{{ item.name.slice(0, 1) }}</span>
    </div>
    <div class="item-body">
      <div class="item-name" :title="item.name">{{ item.name }}</div>
      <div class="item-price">
        <template v-if="priceOptions.length">
          <span
            v-for="(option, index) in priceOptions"
            :key="`${option.currency}-${index}`"
            class="price-option"
            :title="option.currency"
          >
            <span class="currency-chip" :class="currencyClass(option.currency)">
              <img
                v-if="option.icon"
                :src="imageUrl(option.icon)"
                class="currency-icon"
                :alt="option.currency"
                loading="lazy"
              />
              <span class="price-value">{{ option.price }}</span>
            </span>
            <span v-if="index < priceOptions.length - 1" class="price-sep">/</span>
          </span>
        </template>
        <template v-else>
          <span class="price-value">{{ item.price }}</span>
          <span class="price-currency">{{ item.currency }}</span>
        </template>
      </div>
      <div class="item-tags">
        <span class="tag">{{ limitLabel }} {{ item.dailyLimit }}</span>
        <span v-if="showSlot" class="tag slot">槽位 {{ item.slot }}</span>
        <span v-if="item.quantity && item.quantity > 1" class="tag quantity">×{{ item.quantity }}</span>
      </div>
      <div v-if="detailLabel" class="detail-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ detailLabel }}</span>
      </div>
    </div>
  </div>

  <!-- 跟随鼠标的浮窗：teleport 到 body，按鼠标位置定位并做视口边界收敛 -->
  <Teleport to="body">
    <div v-if="tipVisible && detail" ref="tipEl" class="mall-tip" :style="tipStyle">
      <div class="detail">
        <div class="detail-head">
          <img
            v-if="!avatarFailed && detailHeadImage"
            :src="detailHeadImage"
            class="head-avatar"
            :alt="detail.ninjaName || item.name"
            @error="avatarFailed = true"
          />
          <div class="head-text">
            <div class="head-name">{{ detail.ninjaName || item.name }}</div>
            <div class="head-meta">
              <span v-if="isBeast && detail.needFragments" class="chip">
                需 {{ detail.needFragments }} 个碎片招募
              </span>
              <span v-if="isDrops && dropTotal > 0" class="chip">共 {{ dropTotal }} 项掉落</span>
            </div>
          </div>
        </div>

        <!-- 忍者碎片：只显示技能（「招募，升星X所需材料」那句不显示） -->
        <template v-if="isNinja">
          <div v-for="(skill, index) in skills" :key="index" class="skill">
            <div class="skill-icon-wrap">
              <img
                v-if="skill.icon"
                :src="imageUrl(skill.icon)"
                class="skill-icon"
                :alt="skill.name"
                loading="lazy"
              />
              <span v-else class="skill-icon-fallback">{{ skill.slot.slice(0, 1) }}</span>
            </div>
            <div class="skill-body">
              <div class="skill-head">
                <span class="slot" :class="slotClass(skill.slot)">{{ skill.slot }}</span>
                <span class="skill-name">{{ skill.name }}</span>
              </div>
              <div v-if="skill.slot === '奥义'" class="skill-meta">
                <span>冷却回合 {{ skill.cooldown ?? '—' }}</span>
                <span>进场冷却 {{ skill.entryCooldown ?? '—' }}</span>
                <span>消耗查克拉 {{ skill.chakra ?? '—' }}</span>
              </div>
              <p class="skill-desc">
                <template v-for="(segment, si) in segmentsOf(skill.marked || skill.description)" :key="si">
                  <em v-if="segment.hl" class="kw">{{ segment.text }}</em>
                  <span v-else>{{ segment.text }}</span>
                </template>
              </p>
            </div>
          </div>
          <p v-if="skills.length === 0" class="detail-empty">该忍者没有可展示的技能数据</p>
        </template>

        <!-- 通灵兽碎片：需求碎片 + 说明（带高亮） + 上阵忍者属性加成 -->
        <template v-else-if="isBeast">
          <p class="detail-text">
            <template v-for="(segment, si) in segmentsOf(detail.marked || detail.description)" :key="si">
              <em v-if="segment.hl" class="kw">{{ segment.text }}</em>
              <span v-else>{{ segment.text }}</span>
            </template>
          </p>
          <div v-if="bonusItems.length > 0" class="bonus-panel">
            <div class="bonus-title">上阵忍者属性加成</div>
            <div class="bonus-grid">
              <div v-for="entry in bonusItems" :key="entry.label" class="bonus-item">
                <span class="bonus-label">{{ entry.label }}</span>
                <span class="bonus-value">{{ entry.value }}</span>
              </div>
            </div>
          </div>
        </template>

        <!-- 礼包：说明 + 固定/随机掉落 -->
        <template v-else-if="isDrops">
          <p v-if="detail.description" class="detail-text">{{ detail.description }}</p>
          <div v-if="fixedDrops.length > 0" class="drop-group">
            <div class="drop-title">固定掉落</div>
            <ul class="drop-list">
              <li v-for="drop in fixedDrops" :key="`f-${drop.itemId}`">
                <span class="drop-name">{{ drop.name }}</span>
                <span class="drop-count">×{{ drop.count }}</span>
              </li>
            </ul>
          </div>
          <div v-if="randomDrops.length > 0" class="drop-group">
            <div class="drop-title">随机掉落</div>
            <ul class="drop-list">
              <li v-for="drop in randomDrops" :key="`r-${drop.itemId}`">
                <span class="drop-name">{{ drop.name }}</span>
                <span class="drop-count">×{{ drop.count }}</span>
              </li>
            </ul>
          </div>
          <p v-if="dropTotal === 0 && !detail.description" class="detail-empty">
            该礼包没有可展示的掉落数据
          </p>
        </template>

        <!-- 普通材料：只有说明 -->
        <template v-else>
          <p v-if="detail.description" class="detail-text">{{ detail.description }}</p>
          <p v-else class="detail-empty">暂无说明</p>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { MallDrop, MallItem, MallSkill } from '@/types/hyolMall'

interface Segment {
  text: string
  hl: boolean
}

const props = withDefaults(
  defineProps<{
    item: MallItem
    /** 图标地址前缀；商城/彩旗/战场各用自己的图片目录 */
    imageBase?: string
    /** 限购文案：商城/彩旗是「每日限购」，战场是「限购」 */
    limitLabel?: string
    /** 战场的槽位没有展示意义，可隐藏 */
    showSlot?: boolean
    /** 图片缓存版本号：期数据重新导入后变化，强制刷新已缓存的图标 */
    imageVersion?: string
  }>(),
  { imageBase: '', limitLabel: '每日限购', showSlot: true, imageVersion: '2' }
)

/** 忍者技能多，用宽浮窗；其余类型内容少，浮窗收窄 */
const NINJA_TIP_WIDTH = 520
const COMPACT_TIP_WIDTH = 360
/** 鼠标与浮窗的间距、浮窗与视口的留白 */
const CURSOR_GAP = 18
const VIEWPORT_MARGIN = 10
const SHOW_DELAY = 120

const iconFailed = ref(false)
const avatarFailed = ref(false)

const detail = computed(() => props.item.detail || null)
const priceOptions = computed(() => props.item.priceOptions || [])

/** 币种配色：元宝金色、点券绿色，其余走主题蓝 */
const currencyClass = (currency: string): string => {
  if (currency.includes('元宝')) return 'gold'
  if (currency.includes('点券')) return 'green'
  return 'blue'
}
const isNinja = computed(() => detail.value?.type === 'ninja')
const isDrops = computed(() => detail.value?.type === 'drops')
const isBeast = computed(() => detail.value?.type === 'beast')

const skills = computed<MallSkill[]>(() => detail.value?.skills || [])
const fixedDrops = computed<MallDrop[]>(() => detail.value?.fixedDrops || [])
const randomDrops = computed<MallDrop[]>(() => detail.value?.randomDrops || [])
const dropTotal = computed(() => fixedDrops.value.length + randomDrops.value.length)

const detailLabel = computed(() => {
  if (isNinja.value) return '忍者技能'
  if (isBeast.value) return '通灵兽'
  if (isDrops.value) return '礼包掉落'
  if (detail.value) return '物品说明'
  return ''
})

/** 通灵兽的属性加成：只展示有值的项，顺序固定「生命 / 攻击 / 防御 / 忍术」 */
const bonusItems = computed(() => {
  const bonus = detail.value?.bonus
  if (!bonus) return []
  const entries: Array<{ label: string; value?: number }> = [
    { label: '生命', value: bonus.life },
    { label: '攻击', value: bonus.attack },
    { label: '防御', value: bonus.defense },
    { label: '忍术', value: bonus.ninjutsu },
  ]
  return entries.filter((entry) => entry.value !== undefined && entry.value !== null)
})

const imageUrl = (name: string): string => {
  const base = props.imageBase || '/api/hyol/mall/image'
  const version = props.imageVersion ? `?v=${encodeURIComponent(props.imageVersion)}` : ''
  return `${window.location.origin}${base}/${encodeURIComponent(name)}${version}`
}

/** 浮窗头图就是商品自身的图标（和卡片同一张），不依赖忍者图鉴 */
const detailHeadImage = computed(() =>
  props.item.image ? imageUrl(props.item.image) : ''
)

const slotClass = (slot: string): string => {
  if (slot === '奥义') return 'ultimate'
  if (slot === '普攻') return 'normal'
  if (slot === '追打') return 'chase'
  if (slot === '触发') return 'trigger'
  return 'passive'
}

/**
 * 把「说明标记」里的【关键词】切成高亮片段。
 * 只用正则切分 + 文本插值渲染，不走 v-html，避免注入。
 */
const segmentsOf = (raw?: string): Segment[] => {
  const text = (raw || '').trim()
  if (!text) return []
  const result: Segment[] = []
  const pattern = /【([^】]+)】/g
  let last = 0
  let match = pattern.exec(text)
  while (match !== null) {
    if (match.index > last) {
      result.push({ text: text.slice(last, match.index), hl: false })
    }
    result.push({ text: match[1], hl: true })
    last = match.index + match[0].length
    match = pattern.exec(text)
  }
  if (last < text.length) {
    result.push({ text: text.slice(last), hl: false })
  }
  return result
}

// ==================== 跟随鼠标的浮窗定位 ====================

const tipVisible = ref(false)
const tipEl = ref<HTMLElement | null>(null)
const cursor = reactive({ x: 0, y: 0 })
const tipHeight = ref(0)

const tipWidth = computed(() => (isNinja.value ? NINJA_TIP_WIDTH : COMPACT_TIP_WIDTH))

/**
 * 定位规则（保证不被视口边缘裁掉）：
 * 1. 默认放在鼠标右下方（鼠标 + 间距）
 * 2. 右侧放不下就翻到鼠标左侧；上方放不下就翻到鼠标上方
 * 3. 最后统一夹到视口内（留 VIEWPORT_MARGIN 边距）
 */
const tipStyle = computed(() => {
  const width = tipWidth.value
  const height = tipHeight.value
  const maxX = window.innerWidth - VIEWPORT_MARGIN
  const maxY = window.innerHeight - VIEWPORT_MARGIN

  let left = cursor.x + CURSOR_GAP
  if (left + width > maxX) {
    left = cursor.x - width - CURSOR_GAP
  }
  if (left < VIEWPORT_MARGIN) {
    left = VIEWPORT_MARGIN
  }
  if (left + width > maxX) {
    left = Math.max(VIEWPORT_MARGIN, maxX - width)
  }

  let top = cursor.y + CURSOR_GAP
  if (height > 0 && top + height > maxY) {
    top = cursor.y - height - CURSOR_GAP
  }
  if (top < VIEWPORT_MARGIN) {
    top = VIEWPORT_MARGIN
  }
  if (height > 0 && top + height > maxY) {
    top = Math.max(VIEWPORT_MARGIN, maxY - height)
  }

  return {
    left: `${Math.round(left)}px`,
    top: `${Math.round(top)}px`,
    width: `${width}px`,
  }
})

let showTimer: number | null = null

const clearShowTimer = () => {
  if (showTimer !== null) {
    window.clearTimeout(showTimer)
    showTimer = null
  }
}

const measure = () => {
  if (tipEl.value) {
    tipHeight.value = tipEl.value.offsetHeight
  }
}

const onEnter = (event: MouseEvent) => {
  if (!detail.value) return
  cursor.x = event.clientX
  cursor.y = event.clientY
  clearShowTimer()
  showTimer = window.setTimeout(() => {
    tipHeight.value = 0
    tipVisible.value = true
    void nextTick(measure)
  }, SHOW_DELAY)
}

const onMove = (event: MouseEvent) => {
  cursor.x = event.clientX
  cursor.y = event.clientY
  // 内容在显示期间不变，高度只量一次，避免每次 mousemove 都触发一次强制布局
  if (tipVisible.value && tipHeight.value === 0) {
    measure()
  }
}

const onLeave = () => {
  clearShowTimer()
  tipVisible.value = false
}

/** 页面滚动 / 视口尺寸变化时直接收起，避免浮窗停在旧位置 */
const onViewportChange = () => {
  if (tipVisible.value) {
    tipVisible.value = false
  }
  clearShowTimer()
}

onMounted(() => {
  window.addEventListener('scroll', onViewportChange, true)
  window.addEventListener('resize', onViewportChange)
})

onBeforeUnmount(() => {
  clearShowTimer()
  window.removeEventListener('scroll', onViewportChange, true)
  window.removeEventListener('resize', onViewportChange)
})
</script>

<style scoped>
.item-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  height: 100%;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.item-card.hoverable {
  cursor: default;
}

.item-card:hover {
  border-color: rgba(47, 124, 246, 0.4);
  box-shadow: 0 4px 14px rgba(47, 124, 246, 0.1);
}

.item-icon {
  flex: 0 0 68px;
  width: 68px;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(47, 124, 246, 0.06);
  border-radius: 8px;
  overflow: hidden;
}

.item-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.icon-fallback {
  font-size: 24px;
  font-weight: 700;
  color: rgba(47, 124, 246, 0.5);
}

.item-body {
  flex: 1;
  min-width: 0;
}

.item-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #12304f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 4px 0 6px;
}

.price-value {
  font-size: 16px;
  font-weight: 700;
  color: #e08c1a;
  font-variant-numeric: tabular-nums;
}

.price-currency {
  font-size: 11px;
  color: #8aa3bb;
}

/* 新版导出：一个商品可能有元宝 / 点券两种价格 */
.price-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 货币图标放在浅色圆角胶囊里，避免深色游戏图标在白底上糊成一团 */
.currency-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px 2px 4px;
  background: rgba(240, 245, 252, 0.95);
  border: 1px solid rgba(47, 124, 246, 0.14);
  border-radius: 10px;
}

.currency-chip.gold {
  background: rgba(255, 246, 224, 0.95);
  border-color: rgba(216, 155, 22, 0.28);
}

.currency-chip.green {
  background: rgba(232, 248, 238, 0.95);
  border-color: rgba(58, 158, 99, 0.28);
}

.currency-chip.blue {
  background: rgba(238, 244, 255, 0.95);
  border-color: rgba(47, 124, 246, 0.22);
}

.currency-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
  background: #fff;
  border-radius: 50%;
}

.price-sep {
  font-size: 11px;
  color: #b9cbdc;
}

.item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  padding: 1px 8px;
  font-size: 11px;
  color: #2f7cf6;
  background: rgba(47, 124, 246, 0.1);
  border-radius: 10px;
}

.tag.slot {
  color: #8aa3bb;
  background: rgba(138, 163, 187, 0.14);
}

.tag.quantity {
  color: #2f855a;
  background: rgba(58, 158, 99, 0.12);
}

.detail-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 11px;
  color: #2f7cf6;
  opacity: 0.75;
}

/* ===== 跟随鼠标的浮窗 ===== */

.mall-tip {
  position: fixed;
  z-index: 3000;
  /* 不拦截鼠标事件：否则浮窗会盖住卡片触发 mouseleave 造成闪烁 */
  pointer-events: none;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.16);
  border-radius: 8px;
  box-shadow: 0 8px 28px rgba(18, 48, 79, 0.18);
}

.detail {
  padding: 12px 14px;
  /* 不设 max-height/overflow：内容一次显示完，不出滚动条 */
}

.detail-head {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(47, 124, 246, 0.12);
}

.head-avatar {
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
  background: rgba(47, 124, 246, 0.06);
}

.head-text {
  min-width: 0;
}

.head-name {
  font-size: 15px;
  font-weight: 700;
  color: #12304f;
}

.head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.chip {
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  color: #2f7cf6;
  background: rgba(47, 124, 246, 0.1);
  border-radius: 8px;
}

.detail-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  color: #4a6a88;
}

.bonus-panel {
  margin-top: 10px;
  padding: 8px 10px;
  background: rgba(47, 124, 246, 0.05);
  border-radius: 8px;
}

.bonus-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #12304f;
}

.bonus-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.bonus-item {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 12px;
}

.bonus-label {
  color: #8aa3bb;
}

.bonus-value {
  font-weight: 600;
  color: #2f7cf6;
  font-variant-numeric: tabular-nums;
}

.skill {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed rgba(47, 124, 246, 0.12);
}

.skill:last-child {
  border-bottom: none;
}

.skill-icon-wrap {
  flex: 0 0 52px;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: rgba(47, 124, 246, 0.06);
  border-radius: 8px;
}

.skill-icon {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.skill-icon-fallback {
  font-size: 20px;
  font-weight: 700;
  color: #8aa3bb;
}

.skill-body {
  flex: 1;
  min-width: 0;
}

.skill-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-name {
  font-size: 13px;
  font-weight: 600;
  color: #12304f;
}

.slot {
  flex: 0 0 auto;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  color: #fff;
  border-radius: 9px;
  background: #2f7cf6;
}

.slot.ultimate {
  background: #e0662a;
}

.slot.normal {
  background: #2f7cf6;
}

.slot.chase {
  background: #7a5cf0;
}

.slot.trigger {
  background: #c9a227;
}

.slot.passive {
  background: #4aa96c;
}

.skill-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 4px 0 0;
  font-size: 11px;
  color: #8aa3bb;
  font-variant-numeric: tabular-nums;
}

.skill-desc {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: #4a6a88;
}

.kw {
  font-style: normal;
  font-weight: 600;
  color: #d0681c;
}

.drop-group {
  margin-top: 10px;
}

.drop-title {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #12304f;
}

.drop-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.drop-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 8px;
  font-size: 12px;
  color: #4a6a88;
  background: rgba(47, 124, 246, 0.04);
  border-radius: 6px;
}

.drop-list li + li {
  margin-top: 3px;
}

.drop-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drop-count {
  flex: 0 0 auto;
  margin-left: 8px;
  color: #e08c1a;
  font-variant-numeric: tabular-nums;
}

.detail-empty {
  margin: 8px 0 0;
  font-size: 12px;
  color: #8aa3bb;
}
</style>
