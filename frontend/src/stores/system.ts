import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DashboardOverview } from '@/types'

export const useSystemStore = defineStore('system', () => {
  const dashboardData = ref<DashboardOverview | null>(null)
  const loading = ref(false)

  const setDashboardData = (data: DashboardOverview) => {
    dashboardData.value = data
  }

  const setLoading = (state: boolean) => {
    loading.value = state
  }

  return {
    dashboardData,
    loading,
    setDashboardData,
    setLoading
  }
})