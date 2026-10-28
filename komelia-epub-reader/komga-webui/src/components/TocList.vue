<template>
  <v-list v-if="toc"
          v-model:opened="openGroups"
          open-strategy="single"
  >
    <template v-for="t in toc" :key="t.href ?? t.title">
      <v-list-group v-if="t.children?.length"
                    :value="t.href"
      >
        <template v-slot:activator="{ props }">
          <toc-list-item
              :item="t"
              :expanded="openGroups.includes(t.href)"
              @goto="goto"
              @expand="expandGroup"
              v-bind="props"
          />
        </template>

        <toc-list-item v-for="child in t.children"
                       :key="child.href ?? child.title"
                       :item="child"
                       @goto="goto"
                       :class="`ms-${((child.level??1) - 1) * 4}`"
        />
      </v-list-group>

      <!--  Single item    -->
      <template v-else>
        <toc-list-item :item="t" @goto="goto"/>
      </template>
    </template>
  </v-list>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import type {TocEntry} from '@/types/epub'
import TocListItem from '@/components/TocListItem.vue'

const emit = defineEmits<{goto: [element: TocEntry]}>()
defineProps<{toc?: TocEntry[]}>()
// Vuetify models lists of item values, including headings without a target href.
const openGroups = ref<unknown[]>([])

function goto(element: TocEntry): void {
  emit('goto', element)
}

function expandGroup(value: TocEntry['href']): void {
  openGroups.value = openGroups.value.includes(value) ? [] : [value]
}

</script>

<style>
</style>
