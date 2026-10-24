const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  const payload = await response.json()
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

export function registerUser(data) {
  return request('/api/users/register', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function loginUser(data) {
  return request('/api/users/login', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function resetPassword(data) {
  return request('/api/users/reset-password', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function sendCode(account) {
  return request(`/api/users/send-code?account=${encodeURIComponent(account)}`, {
    method: 'POST',
  })
}

export function getUserProfile(userId) {
  return request(`/api/users/${userId}/profile`)
}

export function updateUserProfile(data) {
  return request('/api/users/profile', {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function getInteractionRecords(userId, limit = 20) {
  return request(`/api/folders/interaction/${userId}?limit=${limit}`)
}

export function getUserMessages(userId) {
  return request(`/api/messages/user/${userId}`)
}

export function markMessageRead(messageId) {
  return request(`/api/messages/${messageId}/read`, {
    method: 'PUT',
  })
}
