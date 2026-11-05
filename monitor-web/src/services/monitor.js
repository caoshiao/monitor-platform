import axios from 'axios'
import { getApiBase, getWebsocketUrl } from '../config/runtime'

const http = axios.create({
  baseURL: getApiBase(),
  timeout: 8000
})

export function getSnapshot() {
  return http.get('/api/monitor/snapshot').then((res) => res.data)
}

export function getHealth() {
  return http.get('/api/monitor/health').then((res) => res.data)
}

export function getNodeConfigs() {
  return http.get('/api/admin/nodes').then((res) => res.data)
}

export function getPublicNodeConfigs() {
  return http.get('/api/monitor/node-configs').then((res) => res.data)
}

export function createNodeConfig(payload) {
  return http.post('/api/admin/nodes', payload).then((res) => res.data)
}

export function updateNodeConfig(id, payload) {
  return http.put(`/api/admin/nodes/${id}`, payload).then((res) => res.data)
}

export function deleteNodeConfig(id) {
  return http.delete(`/api/admin/nodes/${id}`)
}

export function getAlertRules() {
  return http.get('/api/admin/alerts/rules').then((res) => res.data)
}

export function createAlertRule(payload) {
  return http.post('/api/admin/alerts/rules', payload).then((res) => res.data)
}

export function updateAlertRule(id, payload) {
  return http.put(`/api/admin/alerts/rules/${id}`, payload).then((res) => res.data)
}

export function deleteAlertRule(id) {
  return http.delete(`/api/admin/alerts/rules/${id}`)
}

export function getActiveAlerts() {
  return http.get('/api/admin/alerts/active').then((res) => res.data)
}

export function getAlertHistory() {
  return http.get('/api/admin/alerts/history').then((res) => res.data)
}

export function acknowledgeAlert(id) {
  return http.put(`/api/admin/alerts/events/${id}/acknowledge`)
}

export function websocketUrl() {
  return getWebsocketUrl()
}

export default http
