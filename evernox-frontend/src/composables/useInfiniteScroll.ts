import { ref, watch, nextTick, onUnmounted } from 'vue'

interface InfiniteScrollOptions {
  /** 提前多少距离触发加载 */
  rootMargin?: string
}

/**
 * 滚动到底部自动加载下一批
 *
 * 把返回的 sentinelRef 绑在列表末尾的占位元素上即可。触发时机与 LazyImage 一致，
 * 都靠 IntersectionObserver，不引入额外依赖。
 *
 * root 采用"就近查找可滚动祖先"：主列表滚在页面上（找不到祖先，退化为视口），
 * 相册详情照片滚在 el-dialog 的 .el-overlay-dialog 里。若用视口当 root，
 * 弹窗内的哨兵会因为一直落在视口范围内而被判定为可见，开局就把所有批次拉完。
 *
 * 是否还有下一批、以及并发去重由调用方在 onLoadMore 里判断。
 *
 * 追加完一批后必须调用 recheck()：哨兵若始终留在可见区域内，交叉状态没有翻转，
 * IntersectionObserver 不会再回调，列表就停在第二批不动了。recheck 重新观察一次，
 * 强制拿到当前的交叉状态。
 */
export function useInfiniteScroll(
  onLoadMore: () => void,
  options: InfiniteScrollOptions = {}
) {
  const { rootMargin = '400px' } = options
  const sentinelRef = ref<HTMLElement | null>(null)

  let observer: IntersectionObserver | null = null

  const findScrollParent = (el: HTMLElement): HTMLElement | null => {
    let node = el.parentElement
    while (node) {
      const { overflowY } = getComputedStyle(node)
      if (overflowY === 'auto' || overflowY === 'scroll') return node
      node = node.parentElement
    }
    return null
  }

  const disconnect = () => {
    observer?.disconnect()
    observer = null
  }

  const observe = (el: HTMLElement | null) => {
    disconnect()
    if (!el || typeof IntersectionObserver === 'undefined') return

    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) onLoadMore()
      },
      { root: findScrollParent(el), rootMargin }
    )
    observer.observe(el)
  }

  watch(sentinelRef, (el) => observe(el), { immediate: true, flush: 'post' })

  onUnmounted(disconnect)

  /** 列表追加后重新观察，让哨兵仍可见的情况也能继续触发 */
  const recheck = async () => {
    await nextTick()
    observe(sentinelRef.value)
  }

  return { sentinelRef, recheck }
}
