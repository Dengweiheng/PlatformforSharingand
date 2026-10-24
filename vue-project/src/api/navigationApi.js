const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request(path) {
  const response = await fetch(`${API_BASE_URL}${path}`)
  const payload = await response.json()

  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

function qs(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, String(value))
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}


export function getHotTags(limit = 12) {
  return request(`/api/navigation/tags/hot${qs({ limit })}`)
}

export function getLatestRank(limit = 10) {
  return request(`/api/navigation/rank/latest${qs({ limit })}`)
}

export function getHotRank(limit = 10) {
  return request(`/api/navigation/rank/hot${qs({ limit })}`)
}

export function searchResources(params) {
  return request(`/api/navigation/search${qs(params)}`)
}
