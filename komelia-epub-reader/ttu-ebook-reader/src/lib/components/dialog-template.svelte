<section class="komelia-dialog rounded-2xl border border-slate-200 bg-white p-6 text-slate-900 shadow-2xl shadow-black/25">
  <h2 class="mb-5 text-xl font-semibold"><slot name="header" /></h2>
  <slot name="content" />
  <footer class="flex flex-wrap items-center justify-end pt-5">
    <slot name="footer" />
  </footer>
</section>
