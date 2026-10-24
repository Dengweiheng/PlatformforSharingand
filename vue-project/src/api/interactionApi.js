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

async function requestRaw(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
    },
  })
  if (!response.ok) {
    throw new Error('请求失败')
  }
  return response.json()
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

export function resolveMediaUrl(url) {
  if (!url) {
    return ''
  }
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }
  return `${API_BASE_URL}${url}`
}

export function listPublicResources() {
  return request('/api/resources')
}

export function recordDownload(resourceId, userId) {
  return request(`/api/resources/${resourceId}/download${query({ userId })}`, {
    method: 'POST',
  })
}

export function listFolders(userId) {
  return request(`/api/folders/user/${userId}`)
}

export function createFolder(userId, folderName) {
  return request(`/api/folders${query({ userId, folderName })}`, {
    method: 'POST',
  })
}

export function addResourceToFolder(folderId, userId, resourceId) {
  return request(`/api/folders/${folderId}/items${query({ userId, resourceId })}`, {
    method: 'POST',
  })
}

export function listFolderItems(folderId, userId) {
  return request(`/api/folders/${folderId}/items/list${query({ userId })}`)
}

export function removeFolderItem(folderId, itemId, userId) {
  return request(`/api/folders/${folderId}/items/${itemId}${query({ userId })}`, {
    method: 'DELETE',
  })
}

export function batchRemoveFolderItems(folderId, userId, itemIds) {
  return request(`/api/folders/${folderId}/items/batch`, {
    method: 'DELETE',
    body: JSON.stringify({ userId, folderId, itemIds }),
  })
}

export function getInteractionRecords(userId, limit = 20) {
  return request(`/api/folders/interaction/${userId}${query({ limit })}`)
}
