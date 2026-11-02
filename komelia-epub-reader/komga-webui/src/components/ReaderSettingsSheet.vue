<template>
  <v-bottom-sheet v-model="open" :close-on-content-click="false" :opacity="0.2"
                  @keydown.esc.stop="open = false" scrollable max-width="600">
    <v-card :theme="theme" data-komelia-chrome data-komelia-popup data-komelia-reader-settings>
      <div class="komelia-sheet-handle" aria-hidden="true"/>
      <v-toolbar class="settings-header" height="48" data-komelia-chrome>
        <v-toolbar-title>{{ t('bookreader.reader_settings') }}</v-toolbar-title>
        <v-btn icon variant="text" :aria-label="t('bookreader.shortcuts.close')" @click="open = false"><v-icon :icon="mdiClose"/></v-btn>
      </v-toolbar>
      <v-card-text class="settings-body">
        <div class="settings-row stepper-row">
          <span class="settings-label">{{ t('epubreader.settings.font_size') }}</span>
          <div class="komelia-settings-stepper">
            <v-btn variant="text" :aria-label="t('epubreader.shortcuts.font_size_decrease')" @click="fontSize -= 10"><v-icon :icon="mdiMinus"/></v-btn>
            <span>{{ fontSize }}%</span>
            <v-btn variant="text" :aria-label="t('epubreader.shortcuts.font_size_increase')" @click="fontSize += 10"><v-icon :icon="mdiPlus"/></v-btn>
          </div>
        </div>
        <div class="settings-row" v-if="fixedLayout">
          <settings-select :items="readingDirs" v-model="readingDirection" :label="t('bookreader.settings.reading_mode')"/>
        </div>
        <div class="settings-row tap-row">
          <span class="settings-label tap-label">{{ t('epubreader.settings.tap_to_turn') }}
            <small>{{ t('epubreader.settings.tap_to_turn_hint') }}</small>
          </span>
          <v-switch v-model="navigationClick" color="primary" hide-details density="compact" :aria-label="t('epubreader.settings.tap_to_turn')"/>
        </div>
        <section class="theme-section" :aria-label="t('epubreader.settings.viewing_theme')">
          <h3>{{ t('epubreader.settings.viewing_theme') }}</h3>
          <div class="komelia-settings-swatches">
            <div v-for="option in appearances" :key="option.value" class="theme-choice">
              <v-btn :value="option.value" :aria-label="option.text" :aria-pressed="appearance === option.value"
                     data-komelia-theme-swatch
                     :style="{'--komelia-swatch-background': option.color, '--komelia-swatch-foreground': option.value === 'readium-night-on' ? '#edf4fb' : '#13283e'}"
                     :color="option.color" :class="option.class" @click="appearance = option.value">
                <span class="theme-sample" aria-hidden="true">Aa</span>
                <span v-if="appearance === option.value" class="theme-check" aria-hidden="true"><v-icon :icon="mdiCheck" size="12"/></span>
              </v-btn>
              <span class="theme-name" aria-hidden="true">{{ option.text }}</span>
            </div>
          </div>
        </section>
        <div class="settings-row" v-if="!fixedLayout">
          <span class="settings-label">{{ t('epubreader.settings.layout') }}</span>
          <v-btn-toggle mandatory v-model="verticalScroll" variant="text" class="komelia-settings-segments" :aria-label="t('epubreader.settings.layout')">
            <v-btn :value="true">{{ t('epubreader.settings.layout_scroll') }}</v-btn>
            <v-btn :value="false">{{ t('epubreader.settings.layout_paginated') }}</v-btn>
          </v-btn-toggle>
        </div>
        <div class="settings-row" v-if="!verticalScroll">
          <span class="settings-label">{{ t('epubreader.settings.column_count') }}</span>
          <v-btn-toggle mandatory v-model="columnCount" variant="text" class="komelia-settings-segments" :aria-label="t('epubreader.settings.column_count')">
            <v-btn v-for="option in columnCounts" :key="option.value" :value="option.value">{{ option.text }}</v-btn>
          </v-btn-toggle>
        </div>
        <div class="settings-row stepper-row">
          <span class="settings-label">{{ t('epubreader.settings.line_height') }}</span>
          <div class="komelia-settings-stepper">
            <v-btn variant="text" :aria-label="t('epubreader.shortcuts.line_height_decrease')" @click="lineHeight -= .1"><v-icon :icon="mdiMinus"/></v-btn>
            <span>{{ Math.round(lineHeight * 100) }}%</span>
            <v-btn variant="text" :aria-label="t('epubreader.shortcuts.line_height_increase')" @click="lineHeight += .1"><v-icon :icon="mdiPlus"/></v-btn>
          </div>
        </div>
        <div class="settings-row margin-row">
          <span class="settings-label">{{ t('epubreader.settings.page_margins') }}</span>
          <v-slider v-model="pageMargins" :aria-label="t('epubreader.settings.page_margins')"
                    min="0.5" max="4" step="0.25" color="primary" hide-details/>
        </div>
      </v-card-text>
    </v-card>
  </v-bottom-sheet>
</template>

<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SettingsSelect from './SettingsSelect.vue'
import {mdiCheck, mdiClose, mdiMinus, mdiPlus} from '@mdi/js'

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
.settings-header { padding-inline: 10px; }
.settings-header :deep(.v-toolbar-title) { margin-inline-start: 16px; font-size: 22px; font-weight: 600; }
.settings-body { padding: 0 26px max(18px, env(safe-area-inset-bottom)) !important; }
.settings-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; min-height: 56px; gap: 8px 12px; padding-block: 4px; border-bottom: 1px solid rgb(var(--v-theme-on-surface), .13); }
.stepper-row { min-height: 48px; padding-block: 0; }
.tap-row { min-height: 72px; }
.settings-label.tap-label { flex-basis: 180px; }
.tap-label small { display: block; font-size: 12px; font-weight: 400; line-height: 18px; margin-top: 2px; opacity: .8; }
.settings-label { flex: 0 0 84px; font-size: 14px; font-weight: 600; }
.settings-row :deep(.v-input) { flex: 1; min-width: 0; }
.settings-row :deep(.v-switch .v-selection-control) { justify-content: end; }
.komelia-settings-stepper { display: grid; grid-template-columns: 48px 64px 48px; justify-content: end; align-items: center; gap: 4px; text-align: center; }
.komelia-settings-stepper > :deep(.v-btn) { position: relative; min-width: 48px; width: 48px; height: 48px; padding: 0; border: 0; border-radius: 10px; }
.komelia-settings-stepper > :deep(.v-btn)::before { content: ''; position: absolute; inset: 4px; border: 1px solid rgb(var(--v-theme-on-surface), .25); border-radius: 10px; pointer-events: none; }
.theme-section { padding-block: 12px; border-bottom: 1px solid rgb(var(--v-theme-on-surface), .13); }
.theme-section h3 { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.komelia-settings-swatches { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.theme-choice { display: flex; flex-direction: column; gap: 6px; text-align: center; min-width: 0; }
.theme-choice > :deep(.v-btn) { position: relative; min-width: 48px; width: 100%; height: 56px; padding: 0; box-shadow: none; }
.theme-sample { font: 28px/1.2 Georgia, serif; text-transform: none; letter-spacing: normal; }
.theme-name { font-size: 12px; line-height: 20px; }
.theme-check { position: absolute; top: 4px; right: 4px; display: grid; place-items: center; width: 16px; height: 16px; border-radius: 50%; background: #70d9ff; color: #00324a; }
.komelia-settings-segments { flex: 1 0 190px; height: 48px; min-height: 48px; gap: 0; border: 0; border-radius: 12px; }
.komelia-settings-segments > :deep(.v-btn) { flex: 1 0 0; min-width: 48px; min-height: 48px; padding-inline: 8px; border-radius: 10px !important; text-transform: none; letter-spacing: normal; font-size: 14px; }
.margin-row { border: 0; }
@media (max-width: 360px) { .settings-body { padding-inline: 20px; } .settings-label { flex-basis: 80px; } .komelia-settings-segments { flex-basis: 180px; } }
@media (min-width: 840px) { .settings-body { padding-bottom: 24px; } }
</style>
