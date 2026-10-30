<template>
  <v-bottom-sheet
      v-model="open"
      :close-on-content-click="false"
      :opacity="0.2"
      @keydown.esc.stop="open = false"
      scrollable
      max-width="500"
  >
    <v-card :theme="theme" data-komelia-chrome data-komelia-popup data-komelia-reader-settings>
      <v-toolbar data-komelia-chrome>
        <v-btn icon variant="text" :aria-label="t('bookreader.shortcuts.close')" @click="open = false">
          <v-icon :icon="mdiClose"/>
        </v-btn>
        <v-toolbar-title>{{ t('bookreader.reader_settings') }}</v-toolbar-title>
      </v-toolbar>
      <v-card-text class="pa-0">
        <v-list>
          <v-list-item>
            <div class="komelia-settings-stepper">
              <v-btn variant="text" :aria-label="t('epubreader.shortcuts.font_size_decrease')" @click="fontSize -= 10">
                <v-icon :icon="mdiFormatTitle"/>
              </v-btn>
              <span>{{ fontSize }}%</span>
              <v-btn variant="text" :aria-label="t('epubreader.shortcuts.font_size_increase')" @click="fontSize += 10">
                <v-icon :icon="mdiFormatTitle"/>
              </v-btn>
            </div>
          </v-list-item>
          <v-list-subheader>{{ t('bookreader.settings.general') }}</v-list-subheader>
          <v-list-item v-if="fixedLayout">
            <settings-select :items="readingDirs" v-model="readingDirection" :label="t('bookreader.settings.reading_mode')"/>
          </v-list-item>
          <v-list-item>
            <v-switch v-model="navigationClick" :label="t('epubreader.settings.tap_to_turn')" color="primary" hide-details/>
          </v-list-item>
          <v-list-subheader>{{ t('bookreader.settings.display') }}</v-list-subheader>
          <v-list-item>
            <div class="komelia-settings-choice-row">
              <span>{{ t('epubreader.settings.viewing_theme') }}</span>
              <div class="komelia-settings-swatches">
                <v-btn
                    v-for="option in appearances" :key="option.value" :value="option.value"
                    :aria-label="option.text" :aria-pressed="appearance === option.value"
                    data-komelia-theme-swatch
                    :style="{'--komelia-swatch-background': option.color, '--komelia-swatch-foreground': option.value === 'readium-night-on' ? '#eef5ff' : '#13283e'}"
                    :color="option.color" :class="option.class" @click="appearance = option.value"
                >
                  <v-icon :icon="mdiCheck" v-if="appearance === option.value"/>
                </v-btn>
              </div>
            </div>
          </v-list-item>
          <v-list-item v-if="!fixedLayout">
            <div class="komelia-settings-choice-row">
              <span>{{ t('epubreader.settings.layout') }}</span>
              <v-btn-toggle mandatory v-model="verticalScroll" variant="text" class="komelia-settings-segments">
                <v-btn :value="true">{{ t('epubreader.settings.layout_scroll') }}</v-btn>
                <v-btn :value="false">{{ t('epubreader.settings.layout_paginated') }}</v-btn>
              </v-btn-toggle>
            </div>
          </v-list-item>
          <v-list-item v-if="!verticalScroll">
            <div class="komelia-settings-choice-row">
              <span>{{ t('epubreader.settings.column_count') }}</span>
              <v-btn-toggle mandatory v-model="columnCount" variant="text" class="komelia-settings-segments">
                <v-btn v-for="option in columnCounts" :key="option.value" :value="option.value">{{ option.text }}</v-btn>
              </v-btn-toggle>
            </div>
          </v-list-item>
          <v-list-item>
            <div class="komelia-settings-stepper">
              <v-btn variant="text" :aria-label="t('epubreader.shortcuts.line_height_decrease')" @click="lineHeight -= .1">
                <v-icon><IconFormatLineSpacingDown/></v-icon>
              </v-btn>
              <span>{{ Math.round(lineHeight * 100) }}%</span>
              <v-btn variant="text" :aria-label="t('epubreader.shortcuts.line_height_increase')" @click="lineHeight += .1">
                <v-icon :icon="mdiFormatLetterSpacing"/>
              </v-btn>
            </div>
          </v-list-item>
          <v-list-item>
            <v-slider v-model="pageMargins" :label="t('epubreader.settings.page_margins')"
                      min="0.5" max="4" step="0.25" show-ticks="always" tick-size="3"/>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </v-bottom-sheet>
</template>

<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SettingsSelect from './SettingsSelect.vue'
import IconFormatLineSpacingDown from './IconFormatLineSpacingDown.vue'
import {mdiCheck, mdiClose, mdiFormatLetterSpacing, mdiFormatTitle} from '@mdi/js'

interface TextChoice { text: string; value: string }
interface ReadingAppearance extends TextChoice { color: string; class: string }
interface DirectionChoice { title: string; value: string }

defineProps<{
  theme: 'light' | 'dark'
  fixedLayout: boolean
  appearances: ReadingAppearance[]
  readingDirs: DirectionChoice[]
  columnCounts: TextChoice[]
}>()
const open = defineModel<boolean>({required: true})
const fontSize = defineModel<number>('fontSize', {required: true})
const lineHeight = defineModel<number>('lineHeight', {required: true})
const appearance = defineModel<string>('appearance', {required: true})
const verticalScroll = defineModel<boolean>('verticalScroll', {required: true})
const columnCount = defineModel<string>('columnCount', {required: true})
const readingDirection = defineModel<string>('readingDirection', {required: true})
const navigationClick = defineModel<boolean>('navigationClick', {required: true})
const pageMargins = defineModel<number>('pageMargins', {required: true})
const {t} = useI18n()
</script>

<style scoped>
.komelia-settings-choice-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding-block: 4px;
}
.komelia-settings-choice-row > span { flex: 0 0 104px; }
.komelia-settings-swatches { display: flex; flex: 1 0 auto; gap: 8px; }
.komelia-settings-swatches > :deep(.v-btn) { min-width: 48px; width: 48px; height: 48px; padding: 0; box-shadow: none; }
.komelia-settings-segments { flex: 1 0 auto; height: auto; min-height: 48px; gap: 4px; padding: 4px; border-radius: 14px; }
.komelia-settings-segments > :deep(.v-btn) { flex: 1 0 auto; min-width: 48px; padding-inline: 8px; border-radius: 10px !important; text-transform: none; letter-spacing: normal; }
.komelia-settings-stepper { display: grid; grid-template-columns: 48px 80px 48px; justify-content: center; align-items: center; gap: 12px; padding-block: 8px; text-align: center; }
.komelia-settings-stepper > :deep(.v-btn) { min-width: 48px; padding: 0; }
@media (max-width: 340px) { .komelia-settings-choice-row > span { flex-basis: 100%; } }
</style>
