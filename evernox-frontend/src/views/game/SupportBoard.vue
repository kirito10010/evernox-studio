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
          <span class="tool-emoji">✏️</span>
          <span>画笔</span>
        </div>
        <div class="tool-btn" :class="{ active: tool === 'eraser' }" title="橡皮擦（只擦自己画的）" @click="tool = 'eraser'">
          <span class="tool-emoji">🧽</span>
          <span>橡皮</span>
        </div>
        <template v-if="isAdmin">
          <div class="tool-btn" :class="{ active: tool === 'lock' }" title="锁定像素（保护成品）" @click="tool = 'lock'">
            <span class="tool-emoji">🔒</span>
            <span>锁定</span>
          </div>
          <div class="tool-btn" :class="{ active: tool === 'unlock' }" title="解锁像素" @click="tool = 'unlock'">
            <span class="tool-emoji">🔓</span>
            <span>解锁</span>
          </div>
        </template>
        <div class="tool-btn" title="适应窗口" @click="fitBoard">
          <span class="tool-emoji">⤢</span>
          <span>适应</span>
        </div>
        <div class="fullscreen-menu">
          <div class="tool-btn trigger" title="全屏" @click="onFullscreenTrigger">
            <span class="tool-emoji">⛶</span>
            <span>{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
          </div>
          <div v-if="!isFullscreen" class="fs-options">
            <div class="fs-option" @click="enterFullscreen('viewport')">视口全屏</div>
            <div class="fs-option" @click="enterFullscreen('browser')">浏览器全屏</div>
          </div>
        </div>
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
            v-if="board"
            ref="hoverCanvasRef"
            :width="board.width"
            :height="board.height"
            class="hover-canvas"
            :style="{
              transform: `translate(${offsetX}px, ${offsetY}px) scale(${scale})`,
            }"
          />
          <div v-if="board" class="board-frame" :style="frameStyle" />
          <div v-if="cursorHintStyle" class="cursor-hint" :style="cursorHintStyle" />
          <el-empty v-else-if="!loading" description="暂无画板，请联系管理员创建" />
        </div>
        <p class="board-hint">滚轮缩放 · 右键拖动 · 左键绘制；橡皮擦只能擦除自己画的点。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  drawSupportPixel,
  getActiveSupportBoard,
  lockSupportPixel,
  lockSupportPixels,
  unlockSupportPixel,
  unlockSupportPixels,
} from '@/api/supportBoard'
import type { SupportBoardView, SupportPixelChange } from '@/api/supportBoard'
import { useUserStore } from '@/stores/user'

const DEFAULT_COLORS = [
  '#000000', '#ffffff', '#ff0000', '#ffa500', '#ffff00', '#00ff00', '#008000',
  '#00ffff', '#0000ff', '#800080', '#ff00ff', '#a52a2a', '#808080', '#ffc0cb',
  '#ff69b4', '#00ced1', '#ffd700', '#ff4500', '#c0c0c0', '#808000', '#800000',
  '#008080', '#000080', '#4b0082', '#ee82ee', '#ff1493', '#1e90ff', '#32cd32',
  '#ff6347', '#dda0dd', '#87ceeb', '#6a5acd', '#7fff00', '#d2b48c', '#bc8f8f',
]
const CUSTOM_KEY = 'support_board_custom_colors'
const MIN_SCALE = 0.1
const MAX_SCALE = 40

const board = ref<SupportBoardView | null>(null)
const loading = ref(false)
type Tool = 'brush' | 'eraser' | 'lock' | 'unlock'
const tool = ref<Tool>('brush')
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const currentColor = ref('#f44336')
const defaultColors = DEFAULT_COLORS
const customColors = ref<string[]>(loadCustomColors())
const customIndexes = [0, 1, 2, 3, 4, 5, 6, 7]
const editingCustom = ref(false)
const isFullscreen = ref(false)

const rootRef = ref<HTMLElement | null>(null)
const wrapRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const hoverCanvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let hoverCtx: CanvasRenderingContext2D | null = null
let eventSource: EventSource | null = null

const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const zoomPercent = computed(() => Math.round(scale.value * 100))
const dragging = ref(false)
const painting = ref(false)
const brushSize = ref(1)
const brushSizes = [1, 3, 5, 9, 17]
const toolSupportsSize = computed(() => tool.value !== 'brush')
const cursorHint = ref<{ x: number; y: number } | null>(null)
const pixelColors = new Map<string, string>()
const lockedSet = new Set<string>()
let paintedKeys = new Set<string>()
let hoverKey = ''
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
  }
})
let dragStartX = 0
let dragStartY = 0
let dragStartOffsetX = 0
let dragStartOffsetY = 0

function loadCustomColors(): string[] {
  try {
    const raw = localStorage.getItem(CUSTOM_KEY)
    if (raw) return JSON.parse(raw) as string[]
  } catch {
    /* 忽略 */
  }
  return ['#ff8a80', '#ffd180', '#ffff8d', '#b9f6ca', '#80d8ff', '#b388ff', '#f48fb1', '#d7ccc8']
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
    for (const p of board.value?.pixels ?? []) {
      pixelColors.set(`${p.x},${p.y}`, p.color)
      if (p.locked === 1) {
        lockedSet.add(`${p.x},${p.y}`)
      }
    }
    await nextTick()
    hoverCtx = hoverCanvasRef.value?.getContext('2d') ?? null
    renderBoard()
    fitBoard()
  } finally {
    loading.value = false
  }
}

const renderBoard = () => {
  const canvas = canvasRef.value
  if (!canvas || !board.value) return
  ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.fillStyle = '#000000'
  ctx.fillRect(0, 0, board.value.width, board.value.height)
  for (const p of board.value.pixels) {
    ctx.fillStyle = p.color
    ctx.fillRect(p.x, p.y, 1, 1)
    if (p.locked === 1) {
      ctx.fillStyle = 'rgba(255, 82, 82, 0.5)'
      ctx.fillRect(p.x, p.y, 1, 1)
    }
  }
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
  if (locked === 1) {
    ctx.fillStyle = 'rgba(255, 82, 82, 0.72)'
    ctx.fillRect(x, y, 1, 1)
  }
}

const fitBoard = () => {
  const wrap = wrapRef.value
  if (!wrap || !board.value) return
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
    painting.value = true
    paintedKeys = new Set()
    paintAt(e.clientX, e.clientY)
  }
}

const onMouseMove = (e: MouseEvent) => {
  if (dragging.value) {
    offsetX.value = dragStartOffsetX + (e.clientX - dragStartX)
    offsetY.value = dragStartOffsetY + (e.clientY - dragStartY)
    return
  }
  const pos = screenToPixel(e.clientX, e.clientY)
  cursorHint.value = pos && toolSupportsSize.value ? pos : null
  if (painting.value) {
    paintAt(e.clientX, e.clientY)
    return
  }
  const key = pos ? `${pos.x},${pos.y}` : ''
  if (key === hoverKey) return
  hoverKey = key
  if (pos && lockedSet.has(key)) {
    drawRegionGlow(pos.x, pos.y)
  } else {
    clearHoverGlow()
  }
}

const onMouseUp = () => {
  dragging.value = false
  painting.value = false
  paintedKeys = new Set()
  hoverKey = ''
  cursorHint.value = null
  clearHoverGlow()
}

const clearHoverGlow = () => {
  if (!hoverCtx || !board.value) return
  hoverCtx.clearRect(0, 0, board.value.width, board.value.height)
}

const drawRegionGlow = (sx: number, sy: number) => {
  if (!hoverCtx || !board.value) return
  const w = board.value.width
  const h = board.value.height
  const region = new Set<string>()
  const queue: [number, number][] = [[sx, sy]]
  region.add(`${sx},${sy}`)
  while (queue.length) {
    const [cx, cy] = queue.pop()!
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]] as const) {
      const nx = cx + dx
      const ny = cy + dy
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
      const k = `${nx},${ny}`
      if (lockedSet.has(k) && !region.has(k)) {
        region.add(k)
        queue.push([nx, ny])
      }
    }
  }
  const boundary: [number, number][] = []
  const outer: [number, number][] = []
  const outerSet = new Set<string>()
  for (const k of region) {
    const [cx, cy] = k.split(',').map(Number)
    let isBoundary = false
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]] as const) {
      const nx = cx + dx
      const ny = cy + dy
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
      const nk = `${nx},${ny}`
      if (!lockedSet.has(nk)) {
        isBoundary = true
        if (!outerSet.has(nk)) {
          outerSet.add(nk)
          outer.push([nx, ny])
        }
      }
    }
    if (isBoundary) boundary.push([cx, cy])
  }
  hoverCtx.clearRect(0, 0, w, h)
  hoverCtx.fillStyle = 'rgba(255, 213, 79, 0.4)'
  for (const [x, y] of outer) hoverCtx.fillRect(x, y, 1, 1)
  hoverCtx.fillStyle = '#FFD54F'
  for (const [x, y] of boundary) hoverCtx.fillRect(x, y, 1, 1)
}

const screenToPixel = (clientX: number, clientY: number) => {
  const wrap = wrapRef.value
  const canvas = canvasRef.value
  if (!wrap || !canvas) return null
  const rect = wrap.getBoundingClientRect()
  const px = clientX - rect.left - offsetX.value
  const py = clientY - rect.top - offsetY.value
  const x = Math.floor(px / scale.value)
  const y = Math.floor(py / scale.value)
  if (x < 0 || y < 0 || x >= canvas.width || y >= canvas.height) return null
  return { x, y }
}

const paintPixel = async (x: number, y: number) => {
  const key = `${x},${y}`
  const color = pixelColors.get(key) ?? '#000000'
  if (tool.value === 'lock') {
    try {
      await lockSupportPixel(x, y)
      applyPixelChange(x, y, color, 1)
    } catch {
      /* 提示已在请求层 */
    }
  } else if (tool.value === 'unlock') {
    try {
      await unlockSupportPixel(x, y)
      applyPixelChange(x, y, color, 0)
    } catch {
      /* 提示已在请求层 */
    }
  } else if (tool.value === 'eraser') {
    try {
      await drawSupportPixel(x, y, null)
      applyPixelChange(x, y, null, null)
    } catch {
      /* 提示已在请求层 */
    }
  } else {
    try {
      await drawSupportPixel(x, y, currentColor.value)
      applyPixelChange(x, y, currentColor.value, 0)
    } catch {
      /* 提示已在请求层 */
    }
  }
}

const paintBatch = async (pixels: { x: number; y: number }[]) => {
  if (tool.value === 'lock') {
    try {
      await lockSupportPixels(pixels)
      for (const p of pixels) {
        applyPixelChange(p.x, p.y, pixelColors.get(`${p.x},${p.y}`) ?? '#000000', 1)
      }
    } catch {
      /* 提示已在请求层 */
    }
  } else if (tool.value === 'unlock') {
    try {
      await unlockSupportPixels(pixels)
      for (const p of pixels) {
        applyPixelChange(p.x, p.y, pixelColors.get(`${p.x},${p.y}`) ?? '#000000', 0)
      }
    } catch {
      /* 提示已在请求层 */
    }
  }
}

const paintAt = (clientX: number, clientY: number) => {
  const pos = screenToPixel(clientX, clientY)
  if (!pos || !board.value) return
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
      paintedKeys.add(key)
      if (tool.value === 'lock' || tool.value === 'unlock') {
        batch.push({ x, y })
      } else {
        paintPixel(x, y)
      }
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
      const data = JSON.parse((e as MessageEvent).data) as SupportPixelChange
      if (board.value && data.boardId === board.value.id) {
        applyPixelChange(data.x, data.y, data.color, data.locked)
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
    flex-wrap: wrap;
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

      .hover-canvas {
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
