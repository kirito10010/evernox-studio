/**
 * 忍者图鉴图片不进构建产物。
 *
 * 这三类图片（heads / portraits / skills）几乎不变，却占了 1996 个文件、30 MB，
 * 每次打包都复制进 dist 会让上传服务器变得非常慢。
 * 它们部署在服务器独立静态目录里，由 Nginx 单独 location 提供；
 * 图鉴数据由后端抓包导入接口提供，dist 里不再保留静态 ninjas.json。
 */
import { rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const targets = ['heads', 'portraits', 'skills']
  .map((dir) => path.join(root, 'dist', 'ninja', dir))

for (const target of targets) {
  const rel = path.relative(root, target)
  try {
    await rm(target, { recursive: true, force: true })
    console.log(`[prune-ninja] removed ${rel}`)
  } catch (error) {
    console.error(`[prune-ninja] failed to remove ${rel}: ${error.message}`)
    process.exitCode = 1
  }
}
