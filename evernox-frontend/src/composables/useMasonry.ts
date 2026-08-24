import { ref, computed, watch, onUnmounted, type Ref } from 'vue'

interface MasonryOptions {
  minColumnWidth?: number
  gap?: number
  maxColumns?: number
}

export function useMasonry<T>(
  items: Ref<T[]>,
  ratioOf: (item: T) => number,
  options: MasonryOptions = {}
) {
  const { minColumnWidth = 260, gap = 16, maxColumns = 10 } = options
  const gridRef = ref<HTMLElement | null>(null)
  const columnCount = ref(1)

  const measure = () => {
    const width = gridRef.value?.clientWidth ?? 0
    if (width <= 0) return
    const n = Math.floor((width + gap) / (minColumnWidth + gap))
    columnCount.value = Math.min(Math.max(n, 1), maxColumns)
  }

  let observer: ResizeObserver | null = null

  // The grid element may appear later (v-if on async data, dialog mount),
  // so bind the observer whenever the element changes instead of on mount only.
  watch(gridRef, (el) => {
    observer?.disconnect()
    observer = null
    if (!el) return
    measure()
    observer = new ResizeObserver(measure)
    observer.observe(el)
  }, { immediate: true, flush: 'post' })

  onUnmounted(() => observer?.disconnect())

  const columns = computed<T[][]>(() => {
    const n = columnCount.value
    const buckets: T[][] = Array.from({ length: n }, () => [])
    const heights = new Array(n).fill(0)
    for (const item of items.value) {
      let target = 0
      for (let i = 1; i < n; i++) if (heights[i] < heights[target]) target = i
      buckets[target].push(item)
      heights[target] += 1 / (ratioOf(item) || 1)
    }
    return buckets
  })

  return { gridRef, columns, columnCount }
}
