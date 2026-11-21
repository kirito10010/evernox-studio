import dayjs from 'dayjs'

export type WorkDayKind = 'work' | 'rest' | 'holiday' | 'makeup'

export interface HolidayPeriod {
  /** 节日名 */
  name: string
  /** 放假首日 YYYY-MM-DD */
  start: string
  /** 放假末日 YYYY-MM-DD */
  end: string
  /** 放假天数 */
  days: number
  /** 调休上班日 YYYY-MM-DD */
  makeup: string[]
}

export interface WorkDayInfo {
  date: string
  kind: WorkDayKind
  /** 裁决说明，如「春节」「调休上班」「小周·周六上班」 */
  label: string
}

export interface CalendarCell extends WorkDayInfo {
  day: number
  inMonth: boolean
  isToday: boolean
}

/** 公司制度：2026-12-01 起由「单休」改为「大小周」 */
export const BIG_SMALL_START = '2026-12-01'
/** 大小周锚点：该周六为大周（休息），此后每 14 天循环一次 */
export const BIG_WEEK_ANCHOR = '2026-12-05'
/** 可浏览的月份范围 */
export const MIN_MONTH = '2026-11'
export const MAX_MONTH = '2027-12'

/** 2027 年法定节假日（国办发明电〔2026〕10 号） */
export const HOLIDAYS_2027: HolidayPeriod[] = [
  { name: '元旦', start: '2027-01-01', end: '2027-01-03', days: 3, makeup: [] },
  {
    name: '春节',
    start: '2027-02-05',
    end: '2027-02-13',
    days: 9,
    makeup: ['2027-01-31', '2027-02-14'],
  },
  { name: '清明节', start: '2027-04-03', end: '2027-04-05', days: 3, makeup: [] },
  { name: '劳动节', start: '2027-05-01', end: '2027-05-05', days: 5, makeup: ['2027-05-08'] },
  { name: '端午节', start: '2027-06-09', end: '2027-06-09', days: 1, makeup: [] },
  { name: '中秋节', start: '2027-09-15', end: '2027-09-15', days: 1, makeup: [] },
  {
    name: '国庆节',
    start: '2027-10-01',
    end: '2027-10-07',
    days: 7,
    makeup: ['2027-09-26', '2027-10-09'],
  },
]

const holidayMap = new Map<string, string>()
const makeupSet = new Set<string>()

for (const period of HOLIDAYS_2027) {
  let cursor = dayjs(period.start)
  const end = dayjs(period.end)
  while (!cursor.isAfter(end, 'day')) {
    holidayMap.set(cursor.format('YYYY-MM-DD'), period.name)
    cursor = cursor.add(1, 'day')
  }
  for (const day of period.makeup) {
    makeupSet.add(day)
  }
}

/** 是否为「大周」的周六（休息） */
const isBigSaturday = (date: string): boolean => {
  const diff = dayjs(date).diff(dayjs(BIG_WEEK_ANCHOR), 'day')
  return ((diff % 14) + 14) % 14 === 0
}

/**
 * 单日裁决：法定节假日 > 法定调休上班 > 公司周规则。
 * 国务院调休把周末调整为全国工作日，所以它优先于公司的周日休息 / 大周周六休息。
 */
export const resolveWorkDay = (date: string): WorkDayInfo => {
  const holiday = holidayMap.get(date)
  if (holiday) return { date, kind: 'holiday', label: holiday }
  if (makeupSet.has(date)) return { date, kind: 'makeup', label: '调休上班' }

  const day = dayjs(date)
  const weekday = day.day()
  if (weekday === 0) return { date, kind: 'rest', label: '周日' }
  if (weekday === 6) {
    if (day.isBefore(dayjs(BIG_SMALL_START))) {
      return { date, kind: 'work', label: '单休·周六上班' }
    }
    return isBigSaturday(date)
      ? { date, kind: 'rest', label: '大周·周六休息' }
      : { date, kind: 'work', label: '小周·周六上班' }
  }
  return { date, kind: 'work', label: '工作日' }
}

/** 生成某月（month: 1-12）的周一起始网格，含跨月补格 */
export const buildMonthGrid = (year: number, month: number): CalendarCell[] => {
  const today = dayjs().format('YYYY-MM-DD')
  const first = dayjs(new Date(year, month - 1, 1))
  const lead = (first.day() + 6) % 7
  const start = first.subtract(lead, 'day')
  const total = Math.ceil((lead + first.daysInMonth()) / 7) * 7

  return Array.from({ length: total }, (_, index) => {
    const current = start.add(index, 'day')
    const date = current.format('YYYY-MM-DD')
    return {
      ...resolveWorkDay(date),
      day: current.date(),
      inMonth: current.month() === month - 1,
      isToday: date === today,
    }
  })
}

/** 当月上班 / 休息天数（不含跨月补格） */
export const monthStats = (cells: CalendarCell[]) => {
  const days = cells.filter((cell) => cell.inMonth)
  return {
    work: days.filter((cell) => cell.kind === 'work' || cell.kind === 'makeup').length,
    rest: days.filter((cell) => cell.kind === 'rest' || cell.kind === 'holiday').length,
  }
}
