<template>
  <div ref="rootRef" class="support-board-page" :class="{ fullscreen: isFullscreen }">
    <div class="palette-bar">
      <div class="current-color" :style="{ background: currentColor }" />
      <div class="palette">
        <span
          v-for="c in defaultColors"
          :key="c"
          class="swatch"
          :class="{ active: currentColor === c }"
          :style="{ background: c }"
          @click="currentColor = c"
        />
        <span class="divider" />
        <template v-if="!editingCustom">
          <span
            v-for="i in customIndexes"
            :key="i"
            class="swatch"
            :class="{ active: currentColor === customColors[i] }"
            :style="{ background: customColors[i] }"
            @click="currentColor = customColors[i]"
          />
        </template>
        <template v-else>
          <input
            v-for="i in customIndexes"
            :key="i"
            type="color"
            class="color-input"
            :value="customColors[i]"
            @input="(e) => onEditColor(i, e)"
          />
        </template>
        <el-button link size="small" @click="editingCustom = !editingCustom">
          {{ editingCustom ? '完成' : '自定义色' }}
        </el-button>
      </div>
      <div class="brush-size" :class="{ disabled: !toolSupportsSize }">
        <span class="brush-size-label">大小</span>
        <span
          v-for="s in brushSizes"
          :key="s"
          class="size-btn"
          :class="{ active: brushSize === s, disabled: !toolSupportsSize }"
          @click="toolSupportsSize && (brushSize = s)"
        >{{ s }}</span>
      </div>
      <div class="zoom-display">{{ zoomPercent }}%</div>
    </div>

    <div class="board-body">
      <div class="tool-bar">
        <div class="tool-btn" :class="{ active: tool === 'brush' }" title="画笔（绘制像素）" @click="tool = 'brush'">
          <el-icon class="tool-emoji"><EditPen /></el-icon>
          <span>画笔</span>
        </div>
        <div class="tool-btn" :class="{ active: tool === 'eraser' }" title="橡皮擦（只擦自己画的）" @click="tool = 'eraser'">
          <el-icon class="tool-emoji"><Delete /></el-icon>
          <span>橡皮</span>
        </div>
        <div class="fullscreen-menu">
          <div class="tool-btn" :class="{ active: showGrid || tool === 'guide' }" title="辅助线">
            <el-icon class="tool-emoji"><Grid /></el-icon>
            <span>辅助线</span>
          </div>
          <div class="fs-options">
            <div class="fs-option" :class="{ active: showGrid }" @click="toggleGrid">格子辅助线</div>
            <div class="fs-option" :class="{ active: tool === 'guide' }" @click="tool = 'guide'">辅助格子</div>
            <div class="fs-option" @click="clearGuideBlocks">清空辅助格子</div>
          </div>
        </div>
        <div class="tool-btn" title="适应窗口" @click="fitBoard">
          <el-icon class="tool-emoji"><Aim /></el-icon>
          <span>适应</span>
        </div>
        <div class="fullscreen-menu">
          <div class="tool-btn trigger" title="全屏" @click="onFullscreenTrigger">
            <el-icon class="tool-emoji"><FullScreen /></el-icon>
            <span>{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
          </div>
          <div v-if="!isFullscreen" class="fs-options">
            <div class="fs-option" @click="enterFullscreen('viewport')">视口全屏</div>
            <div class="fs-option" @click="enterFullscreen('browser')">浏览器全屏</div>
          </div>
        </div>
        <div v-if="isAdmin" class="tool-divider" />
        <template v-if="isAdmin">
          <div class="fullscreen-menu">
            <div class="tool-btn" :class="{ active: tool === 'lock' }" title="锁定像素（保护成品）" @click="selectLockMode(lockMode)">
              <el-icon class="tool-emoji"><Lock /></el-icon>
              <span>{{ lockMode === 'region' ? '区域锁定' : '锁定' }}</span>
            </div>
            <div class="fs-options">
              <div class="fs-option" :class="{ active: tool === 'lock' && lockMode === 'brush' }" @click="selectLockMode('brush')">画笔锁定</div>
              <div class="fs-option" :class="{ active: tool === 'lock' && lockMode === 'region' }" @click="selectLockMode('region')">区域锁定</div>
            </div>
          </div>
          <div class="tool-btn" :class="{ active: tool === 'unlock' }" title="解锁像素" @click="tool = 'unlock'">
            <el-icon class="tool-emoji"><Unlock /></el-icon>
            <span>解锁</span>
          </div>
          <div class="tool-btn" :class="{ active: showLockMark }" title="高亮显示锁定区域" @click="toggleLockMark">
            <el-icon class="tool-emoji"><View /></el-icon>
            <span>锁定标记</span>
          </div>
          <div class="tool-btn" :class="{ active: !showLockStyle }" title="隐藏锁定区域的红色遮罩，查看原图" @click="toggleLockStyle">
            <el-icon class="tool-emoji"><Hide /></el-icon>
            <span>隐藏锁区样式</span>
          </div>
          <div class="tool-btn" title="导入图片成像素画" @click="importDialogVisible = true">
            <el-icon class="tool-emoji"><UploadFilled /></el-icon>
            <span>导入图片</span>
          </div>
        </template>
      </div>

      <div class="canvas-area">
        <div
          v-loading="loading"
          ref="wrapRef"
          class="canvas-wrap"
          :class="{ dragging }"
          @wheel.prevent="onWheel"
          @mousedown="onMouseDown"
          @mousemove="onMouseMove"
          @mouseup="onMouseUp"
          @mouseleave="onMouseUp"
          @contextmenu.prevent
        >
          <canvas
            v-if="board"
            ref="canvasRef"
            :width="board.width"
            :height="board.height"
            class="pixel-canvas"
            :style="{
              transform: `translate(${offsetX}px, ${offsetY}px) scale(${scale})`,
            }"
          />
          <canvas
            v-if="board && showLockMark"
            ref="lockMarkCanvasRef"
            :width="board.width"
            :height="board.height"
            class="lockmark-canvas"
            :style="{
              transform: `translate(${offsetX}px, ${offsetY}px) scale(${scale})`,
            }"
          />
          <canvas
            v-if="board"
            ref="guideCanvasRef"
            :width="board.width"
            :height="board.height"
            class="guide-canvas"
            :style="{
              transform: `translate(${offsetX}px, ${offsetY}px) scale(${scale})`,
            }"
          />
          <div v-if="board" class="board-frame" :style="frameStyle" />
          <div v-if="cursorHintStyle" class="cursor-hint" :style="cursorHintStyle" />
          <div v-if="importState" class="import-toolbar">
            <el-button type="primary" size="small" :loading="importing" @click="confirmImport">
              {{ importing ? `导入中 ${importProgress}%` : '确定' }}
            </el-button>
            <el-button size="small" :disabled="importing" @click="cancelImport">取消</el-button>
            <span class="import-tip">{{ importing ? '正在写入像素…' : '按住拖动调整位置' }}</span>
          </div>
          <div v-if="locking" class="import-toolbar">
            <span class="import-tip">正在锁定 {{ lockProgress }}%…</span>
          </div>
          <el-empty v-if="!board && !loading" description="暂无画板，请联系管理员创建" />
        </div>
        <p class="board-hint">滚轮缩放 · 右键拖动 · 左键绘制；橡皮擦只能擦除自己画的点。</p>
      </div>
    </div>

    <el-dialog v-model="importDialogVisible" title="导入图片成像素画" width="440px">
      <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="(f) => onImportFileChange((f && f.raw) || null)">
        <el-button>选择图片</el-button>
      </el-upload>
      <div v-if="importFile" class="import-file-name">{{ importFile.name }}</div>
      <div class="import-dims">
        <span>像素宽</span>
        <el-input-number v-model="importWidth" :min="1" :max="500" />
        <span class="import-hint">高度按图片比例自动计算</span>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!importFile" @click="startImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  drawSupportPixels,
  getActiveSupportBoard,
  lockSupportPixels,
  unlockSupportPixels,
} from '@/api/supportBoard'
import type { SupportBoardView, SupportPixelChangeEvent } from '@/api/supportBoard'
import { useUserStore } from '@/stores/user'

const DEFAULT_COLORS = [
  '#000000', '#ffffff', '#ff0000', '#ffa500', '#ffff00', '#00ff00', '#008000',
  '#00ffff', '#0000ff', '#800080', '#ff00ff', '#a52a2a', '#808080', '#ffc0cb',
  '#ffd700', '#ff4500',
]
const CUSTOM_KEY = 'support_board_custom_colors'
const MIN_SCALE = 0.1
const MAX_SCALE = 40

const board = ref<SupportBoardView | null>(null)
const loading = ref(false)
type Tool = 'brush' | 'eraser' | 'lock' | 'unlock' | 'guide'
const tool = ref<Tool>('brush')
type LockMode = 'brush' | 'region'
const lockMode = ref<LockMode>('brush')
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const currentColor = ref('#f44336')
const defaultColors = DEFAULT_COLORS
const customColors = ref<string[]>(loadCustomColors())
const customIndexes = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
const editingCustom = ref(false)
const isFullscreen = ref(false)
const showLockMark = ref(false)
const showLockStyle = ref(true)
const hoverLocked = ref(false)

const rootRef = ref<HTMLElement | null>(null)
const wrapRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const guideCanvasRef = ref<HTMLCanvasElement | null>(null)
const lockMarkCanvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let guideCtx: CanvasRenderingContext2D | null = null
let eventSource: EventSource | null = null

const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const zoomPercent = computed(() => Math.round(scale.value * 100))
const dragging = ref(false)
const painting = ref(false)
const brushSize = ref(1)
const brushSizes = [1, 3, 5, 9, 17]
const toolSupportsSize = computed(() => tool.value !== 'brush' && tool.value !== 'guide' && !(tool.value === 'lock' && lockMode.value === 'region'))
const cursorHint = ref<{ x: number; y: number } | null>(null)
const pixelColors = new Map<string, string>()
const lockedSet = new Set<string>()
let paintedKeys = new Set<string>()
let wrapLeft = 0
let wrapTop = 0

const GUIDE_KEY = 'support_board_guide_blocks'
const GRID_KEY = 'support_board_show_grid'
const showGrid = ref(false)
const guideBlocks = ref<string[]>([])
const guideStart = ref<{ x: number; y: number } | null>(null)
const guidePreview = ref<{ x: number; y: number }[]>([])

// 导入图片成像素画
const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importWidth = ref(100)
const importState = ref<{ pixels: string[][]; posX: number; posY: number; width: number; height: number } | null>(null)
const importDragging = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const locking = ref(false)
const lockProgress = ref(0)
let importDragStartX = 0
let importDragStartY = 0
let importDragPosX = 0
let importDragPosY = 0
const frameStyle = computed(() => {
  if (!board.value) return {}
  return {
    left: `${offsetX.value}px`,
    top: `${offsetY.value}px`,
    width: `${board.value.width * scale.value}px`,
    height: `${board.value.height * scale.value}px`,
  }
})

const cursorHintStyle = computed(() => {
  if (!cursorHint.value) return null
  const r = Math.floor(brushSize.value / 2)
  const x = cursorHint.value.x - r
  const y = cursorHint.value.y - r
  return {
    left: `${offsetX.value + x * scale.value}px`,
    top: `${offsetY.value + y * scale.value}px`,
    width: `${brushSize.value * scale.value}px`,
    height: `${brushSize.value * scale.value}px`,
    borderColor: hoverLocked.value ? '#ff5252' : '#ffffff',
  }
})
let dragStartX = 0
let dragStartY = 0
let dragStartOffsetX = 0
let dragStartOffsetY = 0

function loadCustomColors(): string[] {
  const defaults = ['#ff8a80', '#ffd180', '#ffff8d', '#b9f6ca', '#80d8ff', '#b388ff', '#f48fb1', '#d7ccc8', '#a7ffeb', '#ffab91', '#c5e1a5', '#b0bec5']
  try {
    const raw = localStorage.getItem(CUSTOM_KEY)
    if (raw) {
      const saved = JSON.parse(raw) as string[]
      // 旧数据可能不足 12 个，用默认色补齐
      while (saved.length < 12) {
        saved.push(defaults[saved.length])
      }
      return saved.slice(0, 12)
    }
  } catch {
    /* 忽略 */
  }
  return defaults
}

const saveCustomColors = () => {
  localStorage.setItem(CUSTOM_KEY, JSON.stringify(customColors.value))
}

const onEditColor = (i: number, e: Event) => {
  const v = (e.target as HTMLInputElement).value
  if (v) {
    customColors.value[i] = v
    currentColor.value = v
    saveCustomColors()
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await getActiveSupportBoard()
    board.value = res.data ?? null
    pixelColors.clear()
    lockedSet.clear()
    const flatPixels = board.value?.pixels ?? []
    for (let i = 0; i < flatPixels.length; i += 4) {
      const x = flatPixels[i]
      const y = flatPixels[i + 1]
      pixelColors.set(`${x},${y}`, intToHex(flatPixels[i + 2]))
      if (flatPixels[i + 3] === 1) {
        lockedSet.add(`${x},${y}`)
      }
    }
    await nextTick()
    guideCtx = guideCanvasRef.value?.getContext('2d') ?? null
    loadGuideBlocks()
    showGrid.value = localStorage.getItem(GRID_KEY) === '1'
    renderBoard()
    renderGuideOverlay()
    fitBoard()
  } finally {
    loading.value = false
  }
}

const intToHex = (n: number) => `#${(n & 0xffffff).toString(16).padStart(6, '0').toUpperCase()}`

const renderBoard = () => {
  const canvas = canvasRef.value
  if (!canvas || !board.value) return
  ctx = canvas.getContext('2d')
  if (!ctx) return
  const w = board.value.width
  const h = board.value.height
  // 用 ImageData 一次性写入所有像素，比逐点 fillRect 快很多（首次加载/刷新）
  const img = ctx.createImageData(w, h)
  const data = img.data
  data.fill(0)
  for (let i = 3; i < data.length; i += 4) {
    data[i] = 255
  }
  const flat = board.value.pixels
  const showLockOverlay = isAdmin.value && showLockStyle.value
  for (let i = 0; i < flat.length; i += 4) {
    const x = flat[i]
    const y = flat[i + 1]
    const colorInt = flat[i + 2]
    const idx = (y * w + x) * 4
    data[idx] = (colorInt >> 16) & 255
    data[idx + 1] = (colorInt >> 8) & 255
    data[idx + 2] = colorInt & 255
    // 锁定像素直接混入红色，避免逐点 fillRect（锁定像素多时首次加载会很慢）
    if (showLockOverlay && flat[i + 3] === 1) {
      data[idx] = Math.round(data[idx] * 0.28 + 255 * 0.72)
      data[idx + 1] = Math.round(data[idx + 1] * 0.28 + 82 * 0.72)
      data[idx + 2] = Math.round(data[idx + 2] * 0.28 + 82 * 0.72)
    }
  }
  ctx.putImageData(img, 0, 0)
}

const applyPixelChange = (x: number, y: number, color: string | null, locked: number | null) => {
  if (!ctx) return
  const key = `${x},${y}`
  if (color) {
    pixelColors.set(key, color)
  } else {
    pixelColors.delete(key)
  }
  if (locked === 1) {
    lockedSet.add(key)
  } else if (locked === 0) {
    lockedSet.delete(key)
  }
  ctx.fillStyle = color || '#000000'
  ctx.fillRect(x, y, 1, 1)
  if (locked === 1 && isAdmin.value && showLockStyle.value) {
    ctx.fillStyle = 'rgba(255, 82, 82, 0.72)'
    ctx.fillRect(x, y, 1, 1)
  }
}

const pendingChanges: { x: number; y: number; color: string | null; locked: number | null }[] = []
let rafId: number | null = null

const flushPendingChanges = () => {
  rafId = null
  const changes = pendingChanges.splice(0)
  for (const c of changes) {
    applyPixelChange(c.x, c.y, c.color, c.locked)
  }
}

const queuePixelChanges = (changes: { x: number; y: number; color: string | null; locked: number | null }[]) => {
  for (const c of changes) {
    pendingChanges.push(c)
  }
  if (rafId === null) {
    rafId = requestAnimationFrame(flushPendingChanges)
  }
}

const fitBoard = () => {
  const wrap = wrapRef.value
  if (!wrap || !board.value) return
  refreshWrapRect()
  const w = wrap.clientWidth
  const h = wrap.clientHeight
  if (w <= 0 || h <= 0) return
  const s = Math.min(w / board.value.width, h / board.value.height)
  scale.value = Math.max(0.1, s)
  offsetX.value = (w - board.value.width * scale.value) / 2
  offsetY.value = (h - board.value.height * scale.value) / 2
}

const onWheel = (e: WheelEvent) => {
  const wrap = wrapRef.value
  if (!wrap) return
  const rect = wrap.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15
  const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale.value * factor))
  if (newScale === scale.value) return
  offsetX.value = mx - (mx - offsetX.value) * (newScale / scale.value)
  offsetY.value = my - (my - offsetY.value) * (newScale / scale.value)
  scale.value = newScale
}

const onMouseDown = (e: MouseEvent) => {
  if (e.button === 2) {
    dragging.value = true
    dragStartX = e.clientX
    dragStartY = e.clientY
    dragStartOffsetX = offsetX.value
    dragStartOffsetY = offsetY.value
    return
  }
  if (e.button === 0) {
    e.preventDefault()
    refreshWrapRect()
    const pos = screenToPixel(e.clientX, e.clientY)
    if (!pos) return
    if (importState.value) {
      // 导入预览模式：按住拖动预览位置
      importDragging.value = true
      importDragStartX = e.clientX
      importDragStartY = e.clientY
      importDragPosX = importState.value.posX
      importDragPosY = importState.value.posY
      return
    }
    if (tool.value === 'guide') {
      if (guideStart.value) {
        // 第二次点击：结束点，提交连线
        const route = manhattanRoute(guideStart.value.x, guideStart.value.y, pos.x, pos.y)
        for (const p of route) {
          const key = `${p.x},${p.y}`
          if (!guideBlocks.value.includes(key)) {
            guideBlocks.value.push(key)
          }
        }
        guideStart.value = null
        guidePreview.value = []
        saveGuideBlocks()
        renderGuideOverlay()
      } else {
        // 第一次点击：记录起点
        guideStart.value = pos
        guidePreview.value = []
        renderGuideOverlay()
      }
      return
    }
    if (tool.value === 'lock' && lockMode.value === 'region') {
      lockRegionAt(pos.x, pos.y)
      return
    }
    painting.value = true
    paintedKeys = new Set()
    paintAt(pos)
  }
}

const onMouseMove = (e: MouseEvent) => {
  if (dragging.value) {
    offsetX.value = dragStartOffsetX + (e.clientX - dragStartX)
    offsetY.value = dragStartOffsetY + (e.clientY - dragStartY)
    return
  }
  if (importDragging.value && importState.value) {
    const dx = Math.round((e.clientX - importDragStartX) / scale.value)
    const dy = Math.round((e.clientY - importDragStartY) / scale.value)
    importState.value.posX = importDragPosX + dx
    importState.value.posY = importDragPosY + dy
    renderGuideOverlay()
    return
  }
  if (tool.value === 'guide' && guideStart.value) {
    const pos = screenToPixel(e.clientX, e.clientY)
    if (pos) {
      guidePreview.value = manhattanRoute(guideStart.value.x, guideStart.value.y, pos.x, pos.y)
      renderGuideOverlay()
    }
    return
  }
  const pos = screenToPixel(e.clientX, e.clientY)
  cursorHint.value = pos && toolSupportsSize.value ? pos : null
  if (painting.value) {
    if (pos) paintAt(pos)
    return
  }
  // 悬停锁定状态（O(1)，仅用于光标提示颜色，不再做连通块 flood-fill）
  hoverLocked.value = !!pos && isAdmin.value && lockedSet.has(`${pos.x},${pos.y}`)
}

const onMouseUp = () => {
  dragging.value = false
  painting.value = false
  importDragging.value = false
  paintedKeys = new Set()
  hoverLocked.value = false
  cursorHint.value = null
}

const findRegion = (sx: number, sy: number, inRegion: (x: number, y: number) => boolean) => {
  if (!board.value) return []
  const w = board.value.width
  const h = board.value.height
  if (sx < 0 || sy < 0 || sx >= w || sy >= h) return []
  if (!inRegion(sx, sy)) return []
  const region: { x: number; y: number }[] = []
  const visited = new Set<string>()
  const queue: [number, number][] = [[sx, sy]]
  visited.add(`${sx},${sy}`)
  while (queue.length) {
    const [cx, cy] = queue.pop()!
    region.push({ x: cx, y: cy })
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]] as const) {
      const nx = cx + dx
      const ny = cy + dy
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
      const k = `${nx},${ny}`
      if (visited.has(k)) continue
      if (!inRegion(nx, ny)) continue
      visited.add(k)
      queue.push([nx, ny])
    }
  }
  return region
}

// ===== 锁定区域标记（替代 hover flood-fill，改为一次性绘制到独立画布，避免高运算） =====
const renderLockMark = () => {
  const canvas = lockMarkCanvasRef.value
  if (!canvas || !board.value) return
  const c = canvas.getContext('2d')
  if (!c) return
  const w = board.value.width
  const h = board.value.height
  const img = c.createImageData(w, h)
  const d = img.data // 默认全透明
  // 锁定像素标记为半透明品红，艺术内容仍可见，且与普通颜色明显区分
  for (const key of lockedSet) {
    const i = key.indexOf(',')
    const x = Number(key.slice(0, i))
    const y = Number(key.slice(i + 1))
    if (x < 0 || y < 0 || x >= w || y >= h) continue
    const idx = (y * w + x) * 4
    d[idx] = 255
    d[idx + 1] = 40
    d[idx + 2] = 255
    d[idx + 3] = 150
  }
  c.putImageData(img, 0, 0)
}

const toggleLockMark = async () => {
  showLockMark.value = !showLockMark.value
  if (showLockMark.value) {
    await nextTick()
    renderLockMark()
  }
}

const toggleLockStyle = () => {
  showLockStyle.value = !showLockStyle.value
  // 重新渲染主画布，按新的锁区样式重画锁定像素
  renderBoard()
}

const selectLockMode = (mode: LockMode) => {
  lockMode.value = mode
  tool.value = 'lock'
  hoverLocked.value = false
}

const lockRegionAt = async (sx: number, sy: number) => {
  if (locking.value) return
  const region = findRegion(sx, sy, (x, y) => pixelColors.has(`${x},${y}`))
  if (region.length === 0) return
  locking.value = true
  lockProgress.value = 0
  try {
    // 大区域分片锁定，避免单次请求过大超时
    const CHUNK = 10000
    const total = region.length
    let done = 0
    for (let i = 0; i < total; i += CHUNK) {
      const batch = region.slice(i, i + CHUNK)
      await lockSupportPixels(batch)
      queuePixelChanges(
        batch.map((p) => ({
          x: p.x,
          y: p.y,
          color: pixelColors.get(`${p.x},${p.y}`) ?? '#000000',
          locked: 1,
        })),
      )
      done += batch.length
      lockProgress.value = Math.floor((done / total) * 100)
    }
    // 同步锁定集合并刷新锁定标记（避免等待 rAF 才更新）
    for (const p of region) lockedSet.add(`${p.x},${p.y}`)
    if (showLockMark.value) renderLockMark()
    ElMessage.success(`已锁定 ${total} 个像素`)
  } catch {
    ElMessage.error('锁定失败，请重试')
  } finally {
    locking.value = false
    lockProgress.value = 0
  }
}

// ===== 辅助线功能 =====
const manhattanRoute = (x1: number, y1: number, x2: number, y2: number) => {
  const pts: { x: number; y: number }[] = []
  const horizontalFirst = Math.abs(x2 - x1) >= Math.abs(y2 - y1)
  if (horizontalFirst) {
    // 先横后竖
    const stepX = x2 >= x1 ? 1 : -1
    for (let x = x1; x !== x2; x += stepX) pts.push({ x, y: y1 })
    const stepY = y2 >= y1 ? 1 : -1
    for (let y = y1; y !== y2; y += stepY) pts.push({ x: x2, y })
    pts.push({ x: x2, y: y2 })
  } else {
    // 先竖后横
    const stepY = y2 >= y1 ? 1 : -1
    for (let y = y1; y !== y2; y += stepY) pts.push({ x: x1, y })
    const stepX = x2 >= x1 ? 1 : -1
    for (let x = x1; x !== x2; x += stepX) pts.push({ x, y: y2 })
    pts.push({ x: x2, y: y2 })
  }
  return pts
}

const renderGuideOverlay = () => {
  if (!guideCtx || !board.value) return
  const w = board.value.width
  const h = board.value.height
  guideCtx.clearRect(0, 0, w, h)
  // 网格辅助线：每 10 像素一条网格线（1 像素一格会铺满全画板变成白幕）
  if (showGrid.value) {
    const step = 10
    guideCtx.strokeStyle = 'rgba(255, 255, 255, 0.16)'
    guideCtx.lineWidth = 1
    guideCtx.beginPath()
    for (let x = 0; x <= w; x += step) {
      guideCtx.moveTo(x + 0.5, 0)
      guideCtx.lineTo(x + 0.5, h)
    }
    for (let y = 0; y <= h; y += step) {
      guideCtx.moveTo(0, y + 0.5)
      guideCtx.lineTo(w, y + 0.5)
    }
    guideCtx.stroke()
  }
  // 辅助格子（已画好的）
  guideCtx.fillStyle = 'rgba(255, 255, 255, 0.25)'
  for (const key of guideBlocks.value) {
    const [x, y] = key.split(',').map(Number)
    guideCtx.fillRect(x, y, 1, 1)
  }
  // 预览线（绘制中）
  if (guidePreview.value.length) {
    guideCtx.fillStyle = 'rgba(255, 255, 255, 0.4)'
    for (const p of guidePreview.value) {
      guideCtx.fillRect(p.x, p.y, 1, 1)
    }
  }
  // 导入图片预览（浮在画板上可拖动）
  if (importState.value) {
    const s = importState.value
    for (let py = 0; py < s.height; py++) {
      for (let px = 0; px < s.width; px++) {
        const color = s.pixels[py][px]
        if (!color) continue
        guideCtx.fillStyle = color
        guideCtx.fillRect(s.posX + px, s.posY + py, 1, 1)
      }
    }
    guideCtx.strokeStyle = '#ffffff'
    guideCtx.lineWidth = 1
    guideCtx.strokeRect(s.posX + 0.5, s.posY + 0.5, s.width, s.height)
  }
}

const saveGuideBlocks = () => {
  localStorage.setItem(GUIDE_KEY, JSON.stringify(guideBlocks.value))
}

const loadGuideBlocks = () => {
  try {
    const raw = localStorage.getItem(GUIDE_KEY)
    if (raw) guideBlocks.value = JSON.parse(raw) as string[]
  } catch {
    /* 忽略 */
  }
}

const toggleGrid = () => {
  showGrid.value = !showGrid.value
  localStorage.setItem(GRID_KEY, showGrid.value ? '1' : '0')
  renderGuideOverlay()
}

const clearGuideBlocks = () => {
  guideBlocks.value = []
  saveGuideBlocks()
  renderGuideOverlay()
}

// ===== 导入图片成像素画 =====
const loadImagePixels = (file: File, w: number): Promise<{ pixels: string[][]; width: number; height: number }> => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const h = Math.max(1, Math.round((w * img.naturalHeight) / img.naturalWidth))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const c = canvas.getContext('2d')
      if (!c) {
        reject(new Error('canvas'))
        return
      }
      c.drawImage(img, 0, 0, w, h)
      const data = c.getImageData(0, 0, w, h).data
      const pixels: string[][] = []
      for (let y = 0; y < h; y++) {
        const row: string[] = []
        for (let x = 0; x < w; x++) {
          const i = (y * w + x) * 4
          const r = data[i]
          const g = data[i + 1]
          const b = data[i + 2]
          const a = data[i + 3]
          if (a < 128) {
            row.push('') // 透明像素跳过
          } else {
            row.push('#' + ((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1))
          }
        }
        pixels.push(row)
      }
      resolve({ pixels, width: w, height: h })
    }
    img.onerror = () => reject(new Error('image load failed'))
    img.src = URL.createObjectURL(file)
  })
}

const onImportFileChange = (file: File | null) => {
  importFile.value = file
}

const startImport = async () => {
  if (!importFile.value || !board.value) return
  const w = Math.max(1, Math.min(Math.floor(importWidth.value), 500))
  try {
    const { pixels, width, height } = await loadImagePixels(importFile.value, w)
    const posX = Math.floor((board.value.width - width) / 2)
    const posY = Math.floor((board.value.height - height) / 2)
    importState.value = { pixels, posX, posY, width, height }
    importDialogVisible.value = false
    renderGuideOverlay()
  } catch {
    ElMessage.error('图片读取失败，请换一张试试')
  }
}

const confirmImport = async () => {
  if (!importState.value || !board.value || importing.value) return
  const s = importState.value
  const pixels: { x: number; y: number; color: string }[] = []
  let skippedLocked = 0
  for (let py = 0; py < s.height; py++) {
    for (let px = 0; px < s.width; px++) {
      const color = s.pixels[py][px]
      if (!color) continue
      const x = s.posX + px
      const y = s.posY + py
      if (x < 0 || y < 0 || x >= board.value.width || y >= board.value.height) continue
      if (lockedSet.has(`${x},${y}`)) {
        skippedLocked++
        continue
      }
      pixels.push({ x, y, color })
    }
  }
  if (!pixels.length) {
    importState.value = null
    renderGuideOverlay()
    if (skippedLocked) {
      ElMessage.warning('照片全部落在锁定区域，未做任何改动')
    } else {
      ElMessage.info('没有可导入的像素')
    }
    return
  }
  importing.value = true
  importProgress.value = 0
  try {
    const CHUNK = 10000
    const total = pixels.length
    let done = 0
    for (let i = 0; i < total; i += CHUNK) {
      const batch = pixels.slice(i, i + CHUNK)
      await drawSupportPixels(batch)
      queuePixelChanges(batch.map((p) => ({ x: p.x, y: p.y, color: p.color, locked: 0 })))
      done += batch.length
      importProgress.value = Math.floor((done / total) * 100)
    }
    importState.value = null
    renderGuideOverlay()
    ElMessage.success(`已导入 ${total} 个像素${skippedLocked ? `，跳过锁定区域 ${skippedLocked} 个` : ''}`)
  } catch {
    ElMessage.error('导入失败，请重试')
  } finally {
    importing.value = false
  }
}

const cancelImport = () => {
  importState.value = null
  renderGuideOverlay()
}

// 切换工具时清掉未完成的辅助线起点与预览
watch(tool, () => {
  guideStart.value = null
  guidePreview.value = []
  renderGuideOverlay()
})

const refreshWrapRect = () => {
  const rect = wrapRef.value?.getBoundingClientRect()
  if (rect) {
    wrapLeft = rect.left
    wrapTop = rect.top
  }
}

const screenToPixel = (clientX: number, clientY: number) => {
  const canvas = canvasRef.value
  if (!canvas) return null
  const px = clientX - wrapLeft - offsetX.value
  const py = clientY - wrapTop - offsetY.value
  const x = Math.floor(px / scale.value)
  const y = Math.floor(py / scale.value)
  if (x < 0 || y < 0 || x >= canvas.width || y >= canvas.height) return null
  return { x, y }
}

const paintBatch = async (pixels: { x: number; y: number }[]) => {
  if (tool.value === 'lock') {
    // 先本地即时生效（乐观更新），再后台同步
    for (const p of pixels) {
      applyPixelChange(p.x, p.y, pixelColors.get(`${p.x},${p.y}`) ?? '#000000', 1)
    }
    try {
      await lockSupportPixels(pixels)
      if (showLockMark.value) renderLockMark()
    } catch {
      /* 提示已在请求层 */
    }
  } else if (tool.value === 'unlock') {
    for (const p of pixels) {
      applyPixelChange(p.x, p.y, pixelColors.get(`${p.x},${p.y}`) ?? '#000000', 0)
    }
    try {
      await unlockSupportPixels(pixels)
      if (showLockMark.value) renderLockMark()
    } catch {
      /* 提示已在请求层 */
    }
  } else if (tool.value === 'eraser') {
    // 快照原色，用于后端跳过（非本人像素）时回滚
    const snapshots = new Map<string, string>()
    for (const p of pixels) {
      snapshots.set(`${p.x},${p.y}`, pixelColors.get(`${p.x},${p.y}`) ?? '#000000')
      applyPixelChange(p.x, p.y, null, null)
    }
    try {
      const res = await drawSupportPixels(pixels.map((p) => ({ x: p.x, y: p.y, color: null })))
      const skipped = res?.data ?? []
      for (const s of skipped) {
        applyPixelChange(s.x, s.y, snapshots.get(`${s.x},${s.y}`) ?? '#000000', null)
      }
    } catch {
      /* 提示已在请求层 */
    }
  } else {
    for (const p of pixels) {
      applyPixelChange(p.x, p.y, currentColor.value, 0)
    }
    try {
      await drawSupportPixels(pixels.map((p) => ({ x: p.x, y: p.y, color: currentColor.value })))
    } catch {
      /* 提示已在请求层 */
    }
  }
}

const paintAt = (pos: { x: number; y: number }) => {
  if (!board.value) return
  const size = tool.value === 'brush' ? 1 : brushSize.value
  const r = Math.floor(size / 2)
  const batch: { x: number; y: number }[] = []
  for (let dy = -r; dy <= r; dy++) {
    for (let dx = -r; dx <= r; dx++) {
      const x = pos.x + dx
      const y = pos.y + dy
      if (x < 0 || y < 0 || x >= board.value.width || y >= board.value.height) continue
      const key = `${x},${y}`
      if (paintedKeys.has(key)) continue
      // 画笔/橡皮不能作用于已锁定像素
      if ((tool.value === 'brush' || tool.value === 'eraser') && lockedSet.has(key)) continue
      paintedKeys.add(key)
      batch.push({ x, y })
    }
  }
  if (batch.length) {
    paintBatch(batch)
  }
}

const enterFullscreen = async (mode: 'viewport' | 'browser') => {
  isFullscreen.value = true
  if (mode === 'browser') {
    try {
      await rootRef.value?.requestFullscreen()
    } catch {
      /* 浏览器不支持时退化为视口全屏 */
    }
  }
  setTimeout(fitBoard, 150)
}

const onFullscreenTrigger = async () => {
  if (isFullscreen.value) {
    isFullscreen.value = false
    try {
      await document.exitFullscreen()
    } catch {
      /* 忽略 */
    }
    setTimeout(fitBoard, 150)
  }
  // 非全屏时点击主按钮不动作，靠 hover 显示两个选项
}

const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
  setTimeout(fitBoard, 150)
}

const connectStream = () => {
  const token = localStorage.getItem('accessToken')
  if (!token) return
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  eventSource = new EventSource(`${base}/support-board/stream?token=${encodeURIComponent(token)}`)
  eventSource.addEventListener('pixel-changed', (e) => {
    try {
      const data = JSON.parse((e as MessageEvent).data) as SupportPixelChangeEvent
      if (board.value && data.boardId === board.value.id) {
        queuePixelChanges(data.pixels ?? [])
      }
    } catch {
      /* 忽略 */
    }
  })
}

const onResize = () => fitBoard()

onMounted(() => {
  load()
  connectStream()
  window.addEventListener('resize', onResize)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  if (rafId !== null) cancelAnimationFrame(rafId)
  window.removeEventListener('resize', onResize)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped lang="scss">
.support-board-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;

  .palette-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: nowrap;
    padding: 10px 14px;
    border: 1px solid var(--ev-border-subtle);
    border-radius: 12px;
    background: var(--el-bg-color);

    .current-color {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      border: 2px solid #fff;
      box-shadow: 0 0 0 1px var(--ev-border-default);
      flex-shrink: 0;
    }

    .palette {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;

      .swatch {
        width: 24px;
        height: 24px;
        border-radius: 6px;
        cursor: pointer;
        box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);

        &.active {
          box-shadow: 0 0 0 2px var(--ev-primary);
        }
      }

      .divider {
        width: 1px;
        height: 22px;
        background: var(--ev-border-default);
        margin: 0 4px;
      }

      .color-input {
        width: 24px;
        height: 24px;
        padding: 0;
        border: 1px solid var(--ev-border-default);
        border-radius: 6px;
        background: transparent;
        cursor: pointer;

        &::-webkit-color-swatch-wrapper {
          padding: 0;
        }

        &::-webkit-color-swatch {
          border: none;
          border-radius: 5px;
        }
      }
    }

    .zoom-display {
      margin-left: auto;
      font-size: 12px;
      color: var(--ev-text-muted);
      white-space: nowrap;
      user-select: none;
    }

    .brush-size {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-left: 8px;

      &.disabled {
        opacity: 0.4;
        pointer-events: none;
      }

      .brush-size-label {
        font-size: 12px;
        color: var(--ev-text-muted);
      }

      .size-btn {
        min-width: 26px;
        height: 26px;
        padding: 0 6px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 1px solid var(--ev-border-default);
        border-radius: 6px;
        font-size: 12px;
        color: var(--ev-text-secondary);
        cursor: pointer;
        user-select: none;
        transition: all 0.15s ease;

        &:hover {
          border-color: var(--ev-primary);
          color: var(--ev-primary);
        }

        &.active {
          background: var(--ev-primary);
          border-color: var(--ev-primary);
          color: #fff;
        }
      }
    }
  }

  .board-body {
    display: flex;
    gap: 12px;
    flex: 1;
    min-height: 0;
  }

  .tool-bar {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 72px;
    flex-shrink: 0;
    padding: 10px;
    border: 1px solid var(--ev-border-subtle);
    border-radius: 12px;
    background: var(--el-bg-color);

    .tool-divider {
      width: 100%;
      height: 1px;
      background: var(--ev-border-default);
      margin: 4px 0;
    }

    .tool-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      padding: 8px 0;
      border-radius: 10px;
      font-size: 12px;
      color: var(--ev-text-secondary);
      cursor: pointer;
      transition: all 0.2s ease;

      .tool-emoji {
        font-size: 18px;
        line-height: 1;
      }

      &:hover {
        background: var(--ev-bg-tint);
        color: var(--ev-primary);
      }

      &.active {
        background: var(--ev-primary);
        color: #fff;
      }
    }

    .fullscreen-menu {
      position: relative;

      .fs-options {
        display: none;
        position: absolute;
        top: 0;
        left: 100%;
        flex-direction: column;
        gap: 4px;
        padding: 6px;
        background: var(--el-bg-color);
        border: 1px solid var(--ev-border-subtle);
        border-radius: 10px;
        box-shadow: var(--ev-shadow-sm);
        white-space: nowrap;
        z-index: 20;

        .fs-option {
          padding: 6px 10px;
          border-radius: 8px;
          font-size: 13px;
          color: var(--ev-text-secondary);
          cursor: pointer;
          white-space: nowrap;

          &:hover {
            background: var(--ev-bg-tint);
            color: var(--ev-primary);
          }

          &.active {
            background: var(--ev-primary);
            color: #fff;
          }
        }
      }

      &:hover .fs-options {
        display: flex;
      }
    }
  }

  .canvas-area {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;

    .canvas-wrap {
      position: relative;
      overflow: hidden;
      flex: 1;
      min-height: 380px;
      border: 1px solid var(--ev-border-subtle);
      border-radius: 12px;
      background-color: #1e2330;
      background-image:
        radial-gradient(1200px 600px at 50% 0%, rgba(80, 110, 255, 0.12), transparent 70%),
        linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
      background-size: 100% 100%, 40px 40px, 40px 40px;
      cursor: crosshair;

      &.dragging {
        cursor: grabbing;
      }

      .pixel-canvas {
        position: absolute;
        top: 0;
        left: 0;
        transform-origin: 0 0;
        image-rendering: pixelated;
        cursor: crosshair;
      }

      .lockmark-canvas {
        position: absolute;
        top: 0;
        left: 0;
        transform-origin: 0 0;
        image-rendering: pixelated;
        pointer-events: none;
        z-index: 1;
      }

      .guide-canvas {
        position: absolute;
        top: 0;
        left: 0;
        transform-origin: 0 0;
        image-rendering: pixelated;
        pointer-events: none;
        z-index: 2;
      }

      .board-frame {
        position: absolute;
        pointer-events: none;
        z-index: 3;
        box-shadow:
          0 0 0 1px rgba(150, 170, 255, 0.55),
          0 0 20px rgba(100, 130, 255, 0.55),
          0 0 50px rgba(100, 130, 255, 0.35),
          0 0 100px rgba(100, 130, 255, 0.2),
          0 30px 70px rgba(0, 0, 0, 0.6);
      }

      .cursor-hint {
        position: absolute;
        pointer-events: none;
        z-index: 4;
        border: 1px solid #ffffff;
        background: rgba(255, 255, 255, 0.08);
        box-shadow:
          0 0 0 1px rgba(0, 0, 0, 0.6),
          0 0 10px rgba(255, 255, 255, 0.6);
        border-radius: 1px;
      }

      .import-toolbar {
        position: absolute;
        top: 10px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 5;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px 12px;
        background: var(--el-bg-color);
        border: 1px solid var(--ev-border-subtle);
        border-radius: 10px;
        box-shadow: var(--ev-shadow-sm);

        .import-tip {
          font-size: 12px;
          color: var(--ev-text-muted);
          margin-left: 4px;
        }
      }
    }

    .import-file-name {
      margin-top: 10px;
      font-size: 13px;
      color: var(--ev-text-secondary);
      word-break: break-all;
    }

    .import-dims {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 12px;
      font-size: 13px;
      color: var(--ev-text-secondary);

      .el-input-number {
        width: 110px;
      }

      .import-hint {
        font-size: 12px;
        color: var(--ev-text-muted);
        white-space: nowrap;
      }
    }

    .board-hint {
      margin: 8px 0 0;
      font-size: 12px;
      color: var(--ev-text-muted);
    }
  }

  &.fullscreen {
    position: fixed;
    inset: 0;
    z-index: 9999;
    background: #0b0f17;
    padding: 10px;

    .palette-bar {
      background: rgba(16, 22, 34, 0.88);
      backdrop-filter: blur(8px);
      border-color: var(--ev-border-subtle);
    }

    .tool-bar {
      background: rgba(16, 22, 34, 0.88);
      backdrop-filter: blur(8px);
      border-color: var(--ev-border-subtle);
    }

    .board-hint {
      display: none;
    }
  }
}
</style>
