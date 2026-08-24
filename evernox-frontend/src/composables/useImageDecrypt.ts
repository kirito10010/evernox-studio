import { getImageBlob } from '@/api/image'

/**
 * 图片加载 composable
 *
 * 服务端已负责落盘编码与读取解码，前端不再参与任何加解密。
 * 这里只做三件事：带 token 请求图片字节 → 转 Blob → 生成 ObjectURL 并缓存。
 * （`<img src>` 无法携带 Authorization 头，私密图片必须走 XHR + ObjectURL）
 *
 * 函数名沿用历史命名以保持各视图调用方式不变。
 */

/**
 * 全局并发闸门
 *
 * 懒加载下多个图片格子会在同一帧内一起进入视口，若不限流会瞬间发出十几个
 * 原图请求，反而加剧卡顿。闸门放在模块级，跨视图共享同一上限。
 */
const MAX_CONCURRENT = 4
let running = 0
const waiting: (() => void)[] = []

const acquire = (): Promise<void> => {
  if (running < MAX_CONCURRENT) {
    running++
    return Promise.resolve()
  }
  return new Promise((resolve) => waiting.push(resolve))
}

const release = () => {
  const next = waiting.shift()
  // 名额直接转交给排队者，running 保持不变
  if (next) next()
  else running--
}

/**
 * @param fetchBlob 取图片字节的方式，默认走用户通道 `/image/{id}/file`；
 *                  管理员页面需要读取他人私密图，传入管理员通道的取流函数。
 */
export function useImageDecrypt(fetchBlob: (id: number) => Promise<Blob> = getImageBlob) {
  // 缓存已加载的图片 URL
  const imageCache = new Map<number, string>()
  // 在途请求：同一张图被多处同时请求时复用同一个 Promise
  const inflight = new Map<number, Promise<string | null>>()

  /**
   * 获取图片可用于 <img src> 的 ObjectURL
   * @param imageId 图片ID
   * @param _iv 保留参数（服务端管理，前端不再使用）
   * @param _visibility 保留参数（权限由服务端校验）
   */
  const decryptImage = async (
    imageId: number,
    _iv?: string,
    _visibility: number = 0
  ): Promise<string | null> => {
    const cached = imageCache.get(imageId)
    if (cached) return cached

    const pending = inflight.get(imageId)
    if (pending) return pending

    const task = (async () => {
      await acquire()
      try {
        const url = await loadObjectUrl(imageId)
        if (url) imageCache.set(imageId, url)
        return url
      } catch (error) {
        console.error('加载图片失败:', error)
        return null
      } finally {
        release()
        inflight.delete(imageId)
      }
    })()

    inflight.set(imageId, task)
    return task
  }

  /**
   * 清理缓存的 Object URLs
   */
  const clearCache = () => {
    for (const url of imageCache.values()) {
      URL.revokeObjectURL(url)
    }
    imageCache.clear()
    inflight.clear()
  }

  /**
   * 兼容旧接口：过去用于获取解密密钥，现在服务端管理密钥，恒为 null
   */
  const getKey = async (): Promise<null> => null

  const loadObjectUrl = async (imageId: number): Promise<string | null> => {
    // MIME 由响应头给出，无需额外请求详情
    const blob = await fetchBlob(imageId)
    if (!blob || blob.size === 0) {
      return null
    }
    return URL.createObjectURL(blob)
  }

  return {
    decryptImage,
    clearCache,
    getKey,
  }
}
