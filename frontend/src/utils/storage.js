// localStorage 封装，统一 key 前缀避免冲突
const PREFIX = 'cg:'

export const storage = {
  set(key, value) {
    localStorage.setItem(PREFIX + key, JSON.stringify(value))
  },
  get(key) {
    const val = localStorage.getItem(PREFIX + key)
    if (val === null) return null
    try { return JSON.parse(val) } catch { return val }
  },
  remove(key) {
    localStorage.removeItem(PREFIX + key)
  },
  clear() {
    Object.keys(localStorage)
      .filter(k => k.startsWith(PREFIX))
      .forEach(k => localStorage.removeItem(k))
  }
}
