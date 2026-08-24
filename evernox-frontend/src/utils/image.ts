import type { ImageResponse } from '@/api/image'

/** 瀑布流卡片占位比例：优先用后端返回的真实尺寸，缺失时退化为 1:1 */
export const aspectRatioOf = (img: ImageResponse): string =>
  img.width && img.height ? `${img.width} / ${img.height}` : '1 / 1'

const SKELETON_RATIOS = ['3 / 4', '1 / 1', '4 / 3']

/** 骨架屏占位比例：按索引循环，制造瀑布流的错落感 */
export const skeletonRatio = (index: number): string =>
  SKELETON_RATIOS[index % SKELETON_RATIOS.length]

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB']

/** 字节数转可读字符串 */
export const formatBytes = (bytes: number): string => {
  if (!bytes || bytes < 0) return '0 B'
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < BYTE_UNITS.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${BYTE_UNITS[unit]}`
}

/**
 * 照片占用率：本用户占用 / (磁盘剩余空间 + 全平台照片占用)
 *
 * 分母刻意排除磁盘上与本项目无关的数据（系统文件、其他程序），
 * 但必须计入其他用户已经占掉的空间 —— 否则单用户视角下比例会被高估。
 * 相册封面本身就是一条 image 记录，已包含在两个占用值里，无需额外累加。
 * @param myUsed 本用户照片占用字节
 * @param diskFree 磁盘可用字节
 * @param allUsed 全平台照片占用字节（含本用户）
 * @returns 0 ~ 100 的百分比数值，分母为 0 时返回 0
 */
export const photoUsagePercent = (myUsed: number, diskFree: number, allUsed: number): number => {
  const mine = Math.max(0, myUsed || 0)
  const free = Math.max(0, diskFree || 0)
  const all = Math.max(0, allUsed || 0)
  const denominator = free + all
  if (denominator <= 0) return 0
  return (mine / denominator) * 100
}

/**
 * 照片占比：本用户占用 / 全平台照片占用
 *
 * 与 photoUsagePercent 的区别在于分母不含磁盘剩余空间，回答的是
 * 「项目已用的存储里，本用户占了多少」。
 * @returns 0 ~ 100 的百分比数值，分母为 0 时返回 0
 */
export const photoSharePercent = (myUsed: number, allUsed: number): number => {
  const mine = Math.max(0, myUsed || 0)
  const all = Math.max(0, allUsed || 0)
  if (all <= 0) return 0
  return (mine / all) * 100
}

/** 裁剪区域（源图坐标） */
export interface CropRect {
  sx: number
  sy: number
  sw: number
  sh: number
}

/** 把源图按 rect 区域裁剪并缩放到 outW×outH，导出为 JPEG Blob */
export const cropImageToBlob = (
  img: HTMLImageElement,
  rect: CropRect,
  outW: number,
  outH: number,
  quality = 0.9
): Promise<Blob> => {
  const canvas = document.createElement('canvas')
  canvas.width = outW
  canvas.height = outH
  const ctx = canvas.getContext('2d')!
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(img, rect.sx, rect.sy, rect.sw, rect.sh, 0, 0, outW, outH)
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('导出封面失败'))),
      'image/jpeg',
      quality
    )
  })
}
