export type AiModelOption = { id: string; label: string }

export const AI_MODELS: AiModelOption[] = [
  { id: 'openai/gpt-4o-mini', label: 'GPT-4o mini' },
  { id: 'openai/gpt-4o', label: 'GPT-4o' },
  { id: 'openai/gpt-4.1-mini', label: 'GPT-4.1 mini' },
  { id: 'openai/gpt-4.1', label: 'GPT-4.1' },
  { id: 'openai/gpt-5-nano', label: 'GPT-5 nano' },
  { id: 'openai/gpt-5-mini', label: 'GPT-5 mini' },
  { id: 'openai/gpt-5', label: 'GPT-5' },
  { id: 'anthropic/claude-sonnet-4', label: 'Claude Sonnet 4' },
  { id: 'google/gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
  { id: 'deepseek/deepseek-chat', label: 'DeepSeek Chat' },
]

const STORAGE_KEY = 'job-search:ai-model'

export function readAiModel(): string {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored && AI_MODELS.some((model) => model.id === stored)) return stored
  } catch {
    // storage unavailable: fall back to the default
  }
  return AI_MODELS[0].id
}

export function writeAiModel(model: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, model)
  } catch {
    // storage unavailable: the choice lasts until the page closes
  }
}
