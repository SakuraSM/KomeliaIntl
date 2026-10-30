<script lang="ts">
  import {availableSystemFonts$} from "$lib/data/store";
  import {inputClasses} from "$lib/css-classes";
  import {clickOutside} from '$lib/functions/use-click-outside';
  import type {Font} from "$lib/data/fonts";

  interface Props {
    fontValue: Font
    defaultFont: Font,
    family: string
  }

  let {fontValue = $bindable(), defaultFont, family}: Props = $props()

  let textValue = $state('')
  let showOptions = $state(false)
  let fontChoice: HTMLButtonElement | undefined = $state()

  function closeOptions(): void {
    showOptions = false
    fontChoice?.focus()
  }

  function onChoiceKeyDown(event: KeyboardEvent): void {
    if (showOptions && event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      closeOptions()
    }
  }

  $effect(() => {
    textValue = fontValue.displayName
  })

  function onFontChange(): void {
    let fontName = textValue
    if (!fontName) {
      fontValue = defaultFont
    } else {
      fontValue = {displayName: fontName, familyName: fontName}
    }
  }
</script>

<div class="flex items-center relative"
     data-popover
>
  <div class="w-full">
    <button type="button"
            data-komelia-font-choice
            bind:this={fontChoice}
            onkeydown={onChoiceKeyDown}
            class="inline-flex w-full rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
            aria-expanded={showOptions}
            aria-haspopup="true"
            onclick={() =>showOptions = !showOptions}
    >
      <span class="font-name">{textValue}</span>
      <svg class="-mr-1 size-5 text-gray-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" data-slot="icon">
        <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
      </svg>
    </button>
  </div>

  {#if showOptions}
    <div
        class="max-h-80 max-w-60vw absolute inset-x-0 top-full z-10 rounded bg-[#333] text-sm font-bold text-white md:max-w-lg overflow-auto"
        onfocusout={(event) =>  {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) showOptions=false
        }}
    >
      {#each $availableSystemFonts$ as font (font)}
        <button
            type="button"
            data-popover
            onkeydown={onChoiceKeyDown}
            class="block w-full px-4 py-2 text-left text-sm hover:bg-gray-700"
            onclick={() => {
                textValue= font
                onFontChange()
                closeOptions()
            }}
            use:clickOutside={({ target }) => {
              if (!(target instanceof Element && target.closest('[data-popover]'))) {
                  showOptions=false
              }
            }}
            style:font-family={`${font}, ${family.toLowerCase()}`}
        >
          {font}
        </button>
      {/each}
    </div>


  {/if}

</div>

<style>
  [data-komelia-font-choice] { align-items: center; justify-content: space-between; min-height: 48px; gap: 4px; }
  .font-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
