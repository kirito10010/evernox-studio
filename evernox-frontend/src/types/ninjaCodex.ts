/** 忍者图鉴 v2 数据集（后端 /hyol/codex/dataset 输出结构） */

export interface CodexSkill {
  id: string
  name: string
  role: string
  desc: string
  cd: number
  enterCd: number
  mp: number
  /** 静态图标路径：skills/{iconId}.png */
  icon: string
  /** 图标兜底链：主图标 404 时依次尝试（客户端没有打包部分技能图标） */
  iconFallback?: string[]
  immediately: boolean
  /** 状态词条名，解释在 dataset.keywords 里 */
  states: string[]
}

export interface CodexSlot {
  role: string
  initial: string
  /** 突破阶梯，按突破顺序 */
  break?: string[]
  y?: string
  l?: string
}

export interface CodexForm {
  key: string
  label: string
  star: number
  kind: 'base' | 'star' | 'extra'
  slots: CodexSlot[]
}

export interface CodexFlags {
  star: boolean
  break: boolean
  y: boolean
  l: boolean
}

export interface CodexNinja {
  id: string
  name: string
  star: number
  rare: number
  portrait: string
  head: string
  breakthroughStar: number
  /** 属性：水 / 火 / 风 / 雷 / 土 */
  attribute?: string
  /** 官方标签（木叶 / 宇智波一族 / 人柱力 / 男性 ...） */
  tags?: string[]
  flags: CodexFlags
  forms: CodexForm[]
  extraForms?: CodexForm[]
}

export interface CodexStats {
  ninjas: number
  skills: number
  forms: number
  slots: number
  break: number
  y: number
  l: number
  extra: number
}

export interface CodexDataset {
  version: number
  build: string
  generatedAt: string
  stats: CodexStats
  keywords: Record<string, string[]>
  skills: Record<string, CodexSkill>
  ninjas: CodexNinja[]
}

export interface CodexMeta {
  build: string
  generatedAt: string
  stats: CodexStats
  fileCount: number
  imageCount: number
  warnings: string[]
}

export interface CodexImportResult {
  build: string
  ninjaCount: number
  skillCount: number
  formCount: number
  imageCount: number
  warnings: string[]
}
