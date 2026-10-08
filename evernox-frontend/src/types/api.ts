/**
 * 与后端 MyBatis-Plus IPage 序列化结构一致的分页结果。
 *
 * 原先后端返回的分页结构在前端被定义了 3 份（api/image.ts、api/ollamaModel.ts、
 * api/aiNews.ts），字段不一致。这里以超集为准统一，避免消费方拿到 undefined。
 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
