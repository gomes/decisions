// Playground form behaviour: add/remove questions, show the fields that match each question type,
// select/deselect providers and show progress while the server calls the providers.
(() => {
  const list = document.getElementById('questions');
  const template = document.getElementById('question-template');
  if (!list || !template) return;

  const INDEXED = /questions(\[\d+\]|\d+|\[__i__\]|__i__)/g;

  function renumber() {
    list.querySelectorAll('.question').forEach((fieldset, i) => {
      fieldset.querySelectorAll('[name], [id], [for]').forEach(el => {
        for (const attr of ['name', 'id', 'for']) {
          const value = el.getAttribute(attr);
          if (!value) continue;
          el.setAttribute(attr, value.replace(INDEXED, m => (m.includes('[') ? `questions[${i}]` : `questions${i}`)));
        }
      });
      const number = fieldset.querySelector('.q-number');
      if (number) number.textContent = String(i + 1);
    });
  }

  function syncType(select) {
    const fieldset = select.closest('.question');
    if (fieldset) fieldset.dataset.type = select.value;
  }

  document.getElementById('add-question')?.addEventListener('click', () => {
    const fragment = template.content.cloneNode(true);
    list.appendChild(fragment);
    renumber();
    list.lastElementChild?.querySelector('input')?.focus();
  });

  list.addEventListener('click', event => {
    const button = event.target.closest('.remove');
    if (!button) return;
    if (list.querySelectorAll('.question').length <= 1) {
      alert('A requisição precisa de ao menos uma pergunta.');
      return;
    }
    button.closest('.question').remove();
    renumber();
  });

  list.addEventListener('change', event => {
    if (event.target.matches('.q-type')) syncType(event.target);
  });
  list.querySelectorAll('.q-type').forEach(syncType);

  document.querySelectorAll('[data-select]').forEach(button => {
    button.addEventListener('click', () => {
      const check = button.dataset.select === 'all';
      document.querySelectorAll('input[name="providers"]:not([disabled])').forEach(cb => { cb.checked = check; });
    });
  });

  document.getElementById('playground')?.addEventListener('submit', () => {
    const run = document.getElementById('run');
    if (run) {
      run.disabled = true;
      run.textContent = 'Executando…';
    }
  });

  if (document.getElementById('results')) {
    document.getElementById('results').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
})();
