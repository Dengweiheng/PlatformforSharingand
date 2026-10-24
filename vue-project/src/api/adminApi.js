const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
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

export function listUsers(params = {}) {
  return request(`/api/admin/users${query(params)}`)
}

export function updateUserStatus(userId, status) {
  return request(`/api/admin/users/${userId}/status${query({ status })}`, {
    method: 'PUT',
  })
}

export function listUserActions(userId, limit = 50) {
  return request(`/api/admin/users/${userId}/actions${query({ limit })}`)
}

export function listPendingResources() {
  return request('/api/admin/resources/pending')
}

export function auditResource(resourceId, adminId, result, remark = '') {
  return request(`/api/admin/resources/${resourceId}/audit${query({ adminId, result, remark })}`, {
    method: 'PUT',
  })
}

export function dashboardOverview(date) {
  return request(`/api/admin/dashboard/overview${query({ date })}`)
}

export function snapshotDashboard(date) {
  return request(`/api/admin/dashboard/snapshot${query({ date })}`, {
    method: 'POST',
  })
}
