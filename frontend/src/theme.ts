import { computed, ref, watch } from "vue";
import { theme } from "ant-design-vue";

export type ThemePreference = "system" | "light" | "dark";
const storageKey = "infrapilot.theme";
function preference(value: string | null): ThemePreference {
  return value === "light" || value === "dark" ? value : "system";
}
function savedPreference(): ThemePreference {
  try {
    return preference(localStorage.getItem(storageKey));
  } catch {
    return "system";
  }
}
const media = window.matchMedia("(prefers-color-scheme: dark)");
const systemDark = ref(media.matches);
export const themePreference = ref<ThemePreference>(savedPreference());
export const isDark = computed(() =>
  themePreference.value === "system"
    ? systemDark.value
    : themePreference.value === "dark",
);
export const themeConfig = computed(() => ({
  algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
}));
export function setThemePreference(value: ThemePreference) {
  themePreference.value = value;
  try {
    localStorage.setItem(storageKey, value);
  } catch {
    // Appearance still works when storage is unavailable.
  }
}
watch(
  isDark,
  (dark) => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute("content", dark ? "#000000" : "#f5f5f5");
  },
  { immediate: true, flush: "sync" },
);
media.addEventListener("change", (event) => (systemDark.value = event.matches));
window.addEventListener("storage", (event) => {
  if (event.key === storageKey || event.key === null)
    themePreference.value = preference(event.newValue);
});
