// Injected into application pages by ApplyScreen. Everything lives under window.__jinger so it is
// idempotent and easy to call from Kotlin via evaluateJavascript.
(function () {
  if (window.__jinger) return;

  const SKIP_TYPES = new Set(['hidden', 'submit', 'button', 'reset', 'image']);

  function text(el) {
    return (el && (el.innerText || el.textContent) || '').replace(/\s+/g, ' ').trim();
  }

  function isVisible(el) {
    if (el.type === 'file') return true; // often visually hidden behind a styled button
    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden') return false;
    const rect = el.getBoundingClientRect();
    return rect.width > 0 || rect.height > 0 || el.type === 'radio' || el.type === 'checkbox';
  }

  function cssEscape(s) {
    return (window.CSS && CSS.escape) ? CSS.escape(s) : s.replace(/([^\w-])/g, '\\$1');
  }

  function selectorFor(el) {
    if (el.id) return '#' + cssEscape(el.id);
    if (el.name) {
      const tag = el.tagName.toLowerCase();
      const type = el.type && el.type !== tag ? `[type="${el.type}"]` : '';
      // Radio/checkbox groups share a name; the value makes each option unique.
      const value = (el.type === 'radio' || el.type === 'checkbox') && el.value
        ? `[value="${el.value.replace(/"/g, '\\"')}"]` : '';
      return `${tag}${type}[name="${el.name.replace(/"/g, '\\"')}"]${value}`;
    }
    // Fallback: structural path. Stable enough for a single page session.
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && parts.length < 6) {
      let part = node.tagName.toLowerCase();
      const parent = node.parentElement;
      if (parent) {
        const siblings = Array.from(parent.children).filter((c) => c.tagName === node.tagName);
        if (siblings.length > 1) part += `:nth-of-type(${siblings.indexOf(node) + 1})`;
      }
      parts.unshift(part);
      if (node.id) { parts[0] = '#' + cssEscape(node.id); break; }
      node = parent;
    }
    return parts.join(' > ');
  }

  function labelFor(el) {
    const ariaLabel = el.getAttribute('aria-label');
    if (ariaLabel) return ariaLabel.trim();
    const labelledBy = el.getAttribute('aria-labelledby');
    if (labelledBy) {
      const t = labelledBy.split(/\s+/).map((id) => text(document.getElementById(id))).filter(Boolean).join(' ');
      if (t) return t;
    }
    if (el.id) {
      const lab = document.querySelector(`label[for="${cssEscape(el.id)}"]`);
      if (lab && text(lab)) return text(lab);
    }
    const wrapping = el.closest('label');
    if (wrapping) {
      const clone = wrapping.cloneNode(true);
      clone.querySelectorAll('input,select,textarea').forEach((n) => n.remove());
      if (text(clone)) return text(clone);
    }
    // Walk up a few containers looking for the nearest label-ish element above the input.
    let node = el.parentElement;
    for (let depth = 0; node && depth < 4; depth++, node = node.parentElement) {
      const cand = node.querySelector('label, legend, [class*="label" i], h1, h2, h3, h4, h5, h6');
      if (cand && !cand.contains(el) && text(cand)) return text(cand);
    }
    return el.placeholder || el.name || el.id || '';
  }

  function isRequired(el) {
    if (el.required || el.getAttribute('aria-required') === 'true') return true;
    const label = labelFor(el);
    return /\*\s*$/.test(label) || /\brequired\b/i.test(label);
  }

  function cleanLabel(label) {
    return label.replace(/[✱*]/g, ' ').replace(/\s*\(?required\)?\s*$/i, '').replace(/\s+/g, ' ').trim().slice(0, 120);
  }

  function collectFields() {
    const fields = [];
    const seenRadioGroups = new Set();
    const elements = Array.from(document.querySelectorAll('input, textarea, select'));
    for (const el of elements) {
      const type = (el.tagName === 'SELECT') ? 'select' : (el.tagName === 'TEXTAREA') ? 'textarea' : (el.type || 'text').toLowerCase();
      if (SKIP_TYPES.has(type)) continue;
      if (type !== 'file' && el.disabled) continue;
      if (!isVisible(el)) continue;
      if (type === 'radio') {
        if (!el.name || seenRadioGroups.has(el.name)) continue;
        seenRadioGroups.add(el.name);
        const group = Array.from(document.querySelectorAll(`input[type="radio"][name="${el.name.replace(/"/g, '\\"')}"]`));
        const fieldset = el.closest('fieldset, [role="radiogroup"], [role="group"]');
        fields.push({
          selector: `input[type="radio"][name="${el.name}"]`,
          label: cleanLabel(fieldset ? (text(fieldset.querySelector('legend')) || labelFor(el)) : labelFor(el)),
          name: el.name, id: el.id || '', type: 'radio',
          options: group.map((r) => labelFor(r) || r.value),
          required: isRequired(el), value: (group.find((r) => r.checked) || {}).value || '',
        });
        continue;
      }
      const isCombobox = el.getAttribute('role') === 'combobox' || el.getAttribute('aria-autocomplete') === 'list';
      const field = {
        selector: selectorFor(el),
        label: cleanLabel(labelFor(el)),
        name: el.name || '', id: el.id || '',
        type: isCombobox ? 'combobox' : type,
        options: [], required: isRequired(el), value: '',
      };
      if (type === 'select') {
        field.options = Array.from(el.options).map((o) => o.text.trim()).filter((t) => t && !/^(select|choose|please|--)/i.test(t));
        field.value = el.selectedOptions[0] ? el.selectedOptions[0].text.trim() : '';
      } else if (type === 'checkbox') {
        field.value = el.checked ? 'true' : '';
      } else if (type !== 'file') {
        field.value = el.value || '';
      }
      fields.push(field);
    }
    return fields;
  }

  function fire(el, name) {
    el.dispatchEvent(new Event(name, { bubbles: true }));
  }

  function setNativeValue(el, value) {
    // React tracks the value via the native setter; calling it on the prototype keeps React's
    // internal tracker in sync so the subsequent 'input' event is not swallowed.
    const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    el.focus();
    setter.call(el, value);
    fire(el, 'input');
    fire(el, 'change');
    el.blur();
  }

  function matchOption(options, value) {
    const v = (value || '').toLowerCase().trim();
    if (!v) return null;
    return options.find((o) => (o.text || o.label || '').trim().toLowerCase() === v)
      || options.find((o) => (o.text || o.label || '').toLowerCase().includes(v) || v.includes((o.text || o.label || '').toLowerCase()));
  }

  function fillField(selector, value) {
    const el = document.querySelector(selector);
    if (!el) return false;
    const type = el.tagName === 'SELECT' ? 'select' : el.tagName === 'TEXTAREA' ? 'textarea' : (el.type || 'text').toLowerCase();
    if (type === 'select') {
      const opt = matchOption(Array.from(el.options), value);
      if (!opt) return false;
      el.value = opt.value;
      fire(el, 'input');
      fire(el, 'change');
      return true;
    }
    if (type === 'radio') {
      const group = Array.from(document.querySelectorAll(selector));
      const target = matchOption(group.map((r) => ({ text: labelFor(r) || r.value, el: r })), value);
      if (!target) return false;
      target.el.click();
      return true;
    }
    if (type === 'checkbox') {
      const want = /^(true|yes|1|on)$/i.test(value);
      if (el.checked !== want) el.click();
      return true;
    }
    if (type === 'file') return false;
    setNativeValue(el, value);
    return true;
  }

  function fillFields(plan) {
    const results = {};
    for (const item of plan) {
      try { results[item.selector] = fillField(item.selector, item.value); } catch (e) { results[item.selector] = false; }
    }
    return results;
  }

  function highlight(selectors) {
    document.querySelectorAll('[data-jinger-highlight]').forEach((el) => {
      el.style.outline = el.dataset.jingerOutline || '';
      el.removeAttribute('data-jinger-highlight');
    });
    let first = null;
    for (const sel of selectors) {
      document.querySelectorAll(sel).forEach((el) => {
        el.dataset.jingerOutline = el.style.outline;
        el.dataset.jingerHighlight = '1';
        el.style.outline = '3px solid #FFC107';
        el.style.outlineOffset = '2px';
        if (!first) first = el;
      });
    }
    if (first) first.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function scrollTo(selector) {
    const el = document.querySelector(selector);
    if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.focus && el.focus(); }
  }

  const SUBMITTED_RE = /thank you for (applying|your application|your interest)|application (has been |was )?(submitted|received)|we('ve| have) received your application|successfully submitted/i;

  function detectSubmitted() {
    if (/\/(thanks|thank-you|confirmation|success)\b/i.test(location.pathname)) return true;
    const body = document.body ? (document.body.innerText || '') : '';
    return SUBMITTED_RE.test(body.slice(0, 20000));
  }

  window.__jinger = { collectFields, fillFields, fillField, highlight, scrollTo, detectSubmitted };

  // Let Kotlin know when the page thinks the application went through.
  setInterval(function () {
    if (window.JingerBridge && detectSubmitted()) window.JingerBridge.onSubmitted();
  }, 1500);
})();
