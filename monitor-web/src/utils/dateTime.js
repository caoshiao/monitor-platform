const DISPLAY_TIME_ZONE = 'Asia/Shanghai'

/**
 * 将服务端无时区的 UTC 时间转换为上海时区展示文本。
 *
 * @param {string} value 服务端时间
 * @param {boolean} timeOnly 是否只展示时分秒
 * @returns {string} 格式化后的时间
 */
export function formatServerTime(value, timeOnly = false) {
  if (!value) return timeOnly ? '--:--:--' : '-'
  const source = String(value)
  const normalized = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(source) ? source : `${source}Z`
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return source.replace('T', ' ').slice(0, 19)
  const options = {
    timeZone: DISPLAY_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }
  if (!timeOnly) {
    options.year = 'numeric'
    options.month = '2-digit'
    options.day = '2-digit'
  }
  return new Intl.DateTimeFormat('zh-CN', options).format(date).replaceAll('/', '-')
}
