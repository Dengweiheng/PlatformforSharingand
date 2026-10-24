const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function parseResponse(response) {
  const payload = await response.json()
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
    },
  })
  return parseResponse(response)
}


export function listUploaderResources(uploaderId) {
  return request(`/api/resources/uploader/${uploaderId}`)
}

export function listResourceVersions(resourceId) {
  return request(`/api/resources/${resourceId}/versions`)
}

export function uploadResource(formData) {
  return request('/api/resources/upload', {
    method: 'POST',
    body: formData,
  })
}

export function uploadResourceVersion(resourceId, formData) {
  return request(`/api/resources/${resourceId}/versions`, {
    method: 'POST',
    body: formData,
  })
}

export function updateResourceVisibility(resourceId, uploaderId, visibility) {
  return request(`/api/resources/${resourceId}/visibility?uploaderId=${uploaderId}&visibility=${visibility}`, {
    method: 'PUT',
  })
}

export function updateResource(resourceId, formData) {
  return request(`/api/resources/${resourceId}/update`, {
    method: 'POST',
    body: formData,
  })
}

export function deleteResource(resourceId, uploaderId) {
  return request(`/api/resources/${resourceId}?uploaderId=${uploaderId}`, {
    method: 'DELETE',
  })
}

export function buildPreviewUrl(resource) {
  if (!resource?.id) {
    return ''
  }
  const base = API_BASE_URL || ''
  if (resource.previewUrl) {
    return `${base}${resource.previewUrl}`
  }
  return `${base}/api/resources/${resource.id}/preview`
}

export function listPublicResources() {
  return request('/api/resources')
}
