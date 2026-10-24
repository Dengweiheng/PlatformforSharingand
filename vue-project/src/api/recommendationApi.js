const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
    },
  })
  const payload = await response.json()
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

function query(params = {}) {
  const sp = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      sp.append(key, String(value))
    }
  })
  const text = sp.toString()
  return text ? `?${text}` : ''
}

export function getRecommendations(userId, { limit = 10, refresh = false } = {}) {
  return request(`/api/recommendations/${userId}${query({ limit, refresh })}`)
}

export function refreshRecommendations(userId, { limit = 10 } = {}) {
  return request(`/api/recommendations/${userId}/refresh${query({ limit })}`, {
    method: 'POST',
  })
}

export function recordDownloadAction(resourceId, userId) {
  return request(`/api/resources/${resourceId}/download${query({ userId })}`, {
    method: 'POST',
  })
}

export function recordFavoriteAction(resourceId, userId) {
  return request(`/api/resources/${resourceId}/favorite${query({ userId })}`, {
    method: 'POST',
  })
}

export function resolveMediaUrl(url) {
  if (!url) {
    return ''
  }
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }
  return `${API_BASE_URL}${url}`
}
