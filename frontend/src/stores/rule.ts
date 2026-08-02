import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { createRule, deleteRule, getRules, setRuleEnabled, updateRule } from '../api/rules'
import type { ReviewRule, RuleRequest } from '../types/rule'
import { getErrorMessage } from '../types/api'

export const useRuleStore = defineStore('rule', () => {
  const rules = ref<ReviewRule[]>([])
  const languageFilter = ref('')
  const enabledFilter = ref<'all' | 'enabled' | 'disabled'>('all')
  const severityFilter = ref<'all' | 'ERROR' | 'WARNING' | 'INFO'>('all')
  const searchTerm = ref('')
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')

  const languages = computed(() => Array.from(new Set(rules.value.map((item) => item.language).filter((item): item is string => Boolean(item)))).sort())
  const filteredRules = computed(() => {
    const keyword = searchTerm.value.trim().toLowerCase()
    return rules.value.filter((rule) => {
      if (languageFilter.value && rule.language !== languageFilter.value) return false
      if (enabledFilter.value === 'enabled' && !rule.enabled) return false
      if (enabledFilter.value === 'disabled' && rule.enabled) return false
      if (severityFilter.value !== 'all' && rule.severity !== severityFilter.value) return false
      if (keyword && ![rule.ruleId, rule.name, rule.description, rule.language].some((value) => value?.toLowerCase().includes(keyword))) return false
      return true
    })
  })

  async function fetchRules() {
    loading.value = true
    error.value = ''
    try {
      rules.value = await getRules()
    } catch (cause) {
      error.value = getErrorMessage(cause)
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function saveRule(payload: RuleRequest, id?: number) {
    saving.value = true
    try {
      const saved = id ? await updateRule(id, payload) : await createRule(payload)
      const index = rules.value.findIndex((item) => item.id === saved.id)
      if (index >= 0) rules.value[index] = saved
      else rules.value.push(saved)
      return saved
    } finally {
      saving.value = false
    }
  }

  async function toggleRule(rule: ReviewRule) {
    const updated = await setRuleEnabled(rule.id, !rule.enabled)
    const index = rules.value.findIndex((item) => item.id === rule.id)
    if (index >= 0) rules.value[index] = updated
  }

  async function removeRule(rule: ReviewRule) {
    await deleteRule(rule.id)
    rules.value = rules.value.filter((item) => item.id !== rule.id)
  }

  function clearFilters() {
    languageFilter.value = ''
    enabledFilter.value = 'all'
    severityFilter.value = 'all'
    searchTerm.value = ''
  }

  return { rules, filteredRules, languages, languageFilter, enabledFilter, severityFilter, searchTerm, loading, saving, error, fetchRules, saveRule, toggleRule, removeRule, clearFilters }
})
