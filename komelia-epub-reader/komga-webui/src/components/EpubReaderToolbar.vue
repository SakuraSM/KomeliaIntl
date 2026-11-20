<template>
  <header data-komelia-chrome data-komelia-toolbar="top" :aria-label="t('bookreader.shortcuts.reader_navigation')">
    <button type="button" class="komelia-reader-action" :aria-label="t('bookreader.shortcuts.close')" @click="emit('close')">
      <v-icon :icon="mdiArrowLeft" />
    </button>
    <div class="komelia-reader-title">
      <strong :title="title" :data-komelia-reader-series-title="paperTitle && paperTitle !== title ? '' : undefined">{{ title }}</strong>
      <strong v-if="paperTitle && paperTitle !== title" data-komelia-reader-paper-title :title="paperTitle">{{ paperTitle }}</strong>
      <span v-if="subtitle" :title="subtitle">{{ subtitle }}</span>
    </div>
    <button type="button" class="komelia-reader-action" :disabled="!hasNavigation"
            :aria-label="t('epubreader.shortcuts.show_hide_toc')" :aria-expanded="isTocOpen" @click="emit('toggleToc')">
      <v-icon :icon="mdiTableOfContents" />
    </button>
    <button type="button" class="komelia-reader-action" :aria-label="t('bookreader.shortcuts.settings')"
            :aria-expanded="isSettingsOpen" @click="emit('toggleSettings')">
      <v-icon :icon="mdiCog" />
    </button>
    <v-menu location="bottom end" @update:model-value="emit('overflowVisibilityChange', $event)">
      <template #activator="{ props }">
        <button v-bind="props" type="button" class="komelia-reader-action" :aria-label="t('bookreader.shortcuts.more_options')">
          <v-icon :icon="mdiDotsVertical" />
        </button>
      </template>
      <v-list data-komelia-chrome role="menu" density="compact" elevation="0" min-width="220" :aria-label="t('bookreader.shortcuts.more_options')">
        <v-list-item v-if="isFullscreenAvailable" tag="button" type="button" role="menuitem" :title="t('bookreader.shortcuts.fullscreen')" :prepend-icon="mdiFullscreen"
                     @click="emit('fullscreen')" />
        <v-list-item tag="button" type="button" role="menuitem" :title="t('bookreader.shortcuts.show_hide_help')" :prepend-icon="mdiHelpCircle" @click="emit('toggleHelp')" />
        <template v-if="bookNavigation">
          <v-list-item data-komelia-reader-book-navigation tag="button" type="button" role="menuitem"
                       :title="t('epubreader.previous_book')" :prepend-icon="mdiUndo" :disabled="bookNavigation.previousDisabled"
                       :aria-disabled="bookNavigation.previousDisabled"
                       @click="!bookNavigation.previousDisabled && emit('previousBook')" />
          <v-list-item data-komelia-reader-book-navigation tag="button" type="button" role="menuitem"
                       :title="t('epubreader.next_book')" :prepend-icon="mdiRedo" :disabled="bookNavigation.nextDisabled"
                       :aria-disabled="bookNavigation.nextDisabled"
                       @click="!bookNavigation.nextDisabled && emit('nextBook')" />
        </template>
      </v-list>
    </v-menu>
  </header>
</template>

<script setup lang="ts">
import { mdiArrowLeft, mdiCog, mdiDotsVertical, mdiFullscreen, mdiHelpCircle, mdiRedo, mdiTableOfContents, mdiUndo } from '@mdi/js'
import { useI18n } from 'vue-i18n'

defineProps<{
  title: string
  paperTitle?: string
  subtitle?: string
  hasNavigation: boolean
  isTocOpen: boolean
  isSettingsOpen: boolean
  isFullscreenAvailable: boolean
  bookNavigation?: { previousDisabled: boolean; nextDisabled: boolean }
}>()
const emit = defineEmits<{
  close: []
  toggleToc: []
  toggleSettings: []
  toggleHelp: []
  fullscreen: []
  previousBook: []
  nextBook: []
  overflowVisibilityChange: [isOpen: boolean]
}>()
const { t } = useI18n()
</script>

<style scoped>
header { position: fixed; top: 0; z-index: 14; }
</style>
