import http from './http'

export async function login(payload) {
  const data = await http.post('/auth/login', payload, { skipAuthRedirect: true })
  return {
    accessToken: (data && (data.accessToken || data.token)) || '',
    expiresIn: data && data.expiresIn,
    user: (data && data.user) || null
  }
}

export function register(payload) {
  return http.post('/auth/register', payload)
}

export function fetchMe() {
  return http.get('/auth/me')
}

export function updateProfile(payload) {
  return http.put('/auth/me', payload)
}
