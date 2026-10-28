import axios from 'axios'

export const javaHttp = axios.create({
  baseURL: import.meta.env.VITE_JAVA_BASE || '',
  timeout: 60000,
})

export const pythonHttp = axios.create({
  baseURL: import.meta.env.VITE_PYTHON_BASE || '',
  timeout: 60000,
})
