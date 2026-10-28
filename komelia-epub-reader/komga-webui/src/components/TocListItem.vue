<template>
  <v-list-item
      role="listitem"
      :aria-selected="undefined"
      :active="item.current"
      :link="false"
      color="primary"
  >
    <v-list-item-title class="toc-title">
      <button type="button" class="toc-chapter-link" :aria-current="item.current ? 'page' : undefined"
              @click.stop="goto(item)">
        {{ item.title }}
      </button>
    </v-list-item-title>

    <template v-if="item.children?.length" v-slot:append>
      <button
          type="button"
          class="toc-expand"
          :aria-label="t(expanded ? 'epubreader.collapse_section' : 'epubreader.expand_section', {title: item.title})"
          :aria-expanded="expanded ? 'true' : 'false'"
          @click.stop="expand"
      >
        <v-icon :icon="expandIcon" size="20"/>
      </button>
    </template>
  </v-list-item>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TocEntry} from '@/types/epub'
import {mdiChevronDown, mdiChevronUp} from "@mdi/js";

const emit = defineEmits<{goto: [element: TocEntry]; expand: [href: TocEntry['href']]}>()
const props = defineProps<{item: TocEntry; expanded?: boolean}>()
const {t} = useI18n()

function goto(element: TocEntry): void {
  emit('goto', element)
}

const expandIcon = computed(() => props.expanded ? mdiChevronUp : mdiChevronDown)

function expand(): void {
  emit('expand', props.item.href)
}

</script>

<style scoped>
.toc-title { white-space: normal; }
.toc-chapter-link,
.toc-expand {
  --toc-touch-target: 48px;
  border: 0;
  padding: 0;
  color: inherit;
  background: transparent;
  font: inherit;
  cursor: pointer;
}
.toc-chapter-link {
  width: 100%;
  min-height: var(--toc-touch-target);
  text-align: start;
  white-space: normal;
}
.toc-expand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 var(--toc-touch-target);
  width: var(--toc-touch-target);
  height: var(--toc-touch-target);
  border-radius: 50%;
}
.toc-chapter-link:focus-visible,
.toc-expand:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: -2px;
}
</style>
