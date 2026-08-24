import { updateImageDimensions } from '@/api/image'
import type { ImageResponse } from '@/api/image'

/**
 * 图片尺寸自愈
 *
 * 库中的 width/height 是上传时由浏览器测量后提交的，可能因 EXIF 方向或历史
 * 导入数据而与真实像素不符。LazyImage 在渲染时已用真实尺寸纠正了容器比例，
 * 这里顺手把偏差回写数据库，让后续加载的占位比例一开始就是对的
 * （占位比例还决定瀑布流分列高度估算，不修就一直算歪）。
 */

/** 比例偏差在此范围内视为一致，避免亚像素抖动引发无意义写库 */
const RATIO_TOLERANCE = 0.01

/** 同一会话内同一张图只上报一次 */
const reported = new Set<number>()

export function useDimensionSelfHeal() {
  const maybeReport = (img: ImageResponse, width: number, height: number) => {
    if (!width || !height) return
    if (reported.has(img.id)) return

    const actual = width / height
    if (img.width && img.height) {
      const expected = img.width / img.height
      if (Math.abs(expected - actual) / actual < RATIO_TOLERANCE) return
    }

    reported.add(img.id)
    // 后台自愈：失败不打扰用户，也不阻塞渲染；放开标记以便下次重试
    void updateImageDimensions(img.id, width, height).catch(() => reported.delete(img.id))
  }

  return { maybeReport }
}
