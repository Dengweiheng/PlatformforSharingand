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
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') {
      sp.append(k, String(v))
    }
  })
  const text = sp.toString()
  return text ? `?${text}` : ''
}

export function listCommunityPosts(params = {}) {
  return request(`/api/community/posts${query(params)}`)
}

export function createCommunityPost(data) {
  return request('/api/community/posts', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function listPostComments(postId) {
  return request(`/api/community/posts/${postId}/comments`)
}

export function createPostComment(postId, data) {
  return request(`/api/community/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function deleteCommunityPost(postId, userId) {
  return request(`/api/community/posts/${postId}?userId=${userId}`, {
    method: 'DELETE',
  })
}

export function updateCommunityPost(postId, data) {
  return request(`/api/community/posts/${postId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function deleteCommunityComment(commentId, userId) {
  return request(`/api/community/comments/${commentId}?userId=${userId}`, {
    method: 'DELETE',
  })
}
