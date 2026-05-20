(function () {
    var DEBOUNCE_MS = 500;

    var lang = document.documentElement.lang || 'uk';
    var i18n = {
        uk: { prompt: 'Знайдено незбережені дані. Відновити?', yes: 'Так', no: 'Ні' },
        pl: { prompt: 'Znaleziono niezapisane dane. Przywrócić?', yes: 'Tak', no: 'Nie' },
        en: { prompt: 'Unsaved data found. Restore?', yes: 'Yes', no: 'No' }
    };
    var t = i18n[lang] || i18n['uk'];

    function debounce(fn, ms) {
        var timer;
        return function () {
            var args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(this, args); }, ms);
        };
    }

    function shouldSkip(el) {
        return !el.name || el.disabled || el.type === 'password' || el.type === 'hidden';
    }

    function getFormValues(form) {
        var data = {};
        form.querySelectorAll('input, select, textarea').forEach(function (el) {
            if (shouldSkip(el)) return;
            if (el.type === 'checkbox' || el.type === 'radio') {
                data[el.name] = el.checked;
            } else {
                data[el.name] = el.value;
            }
        });
        return data;
    }

    function restoreFormValues(form, data) {
        form.querySelectorAll('input, select, textarea').forEach(function (el) {
            if (shouldSkip(el)) return;
            if (!(el.name in data)) return;
            if (el.type === 'checkbox' || el.type === 'radio') {
                el.checked = data[el.name];
            } else if (el.tagName === 'SELECT' && el.tomselect) {
                el.tomselect.setValue(data[el.name]);
            } else {
                el.value = data[el.name];
            }
            if (el.tagName === 'SELECT') {
                el.dispatchEvent(new Event('change'));
            }
        });
    }

    function hasNonEmptyValues(data) {
        return Object.keys(data).some(function (k) {
            var v = data[k];
            return v !== '' && v !== false && v !== null && v !== undefined;
        });
    }

    function showRestoreBanner(form, key) {
        var banner = document.createElement('div');
        banner.className = 'alert alert-info alert-dismissible fade show mb-3';
        banner.innerHTML =
            '<span>' + t.prompt + '</span>' +
            ' <button type="button" class="btn btn-sm btn-primary ms-2">' + t.yes + '</button>' +
            ' <button type="button" class="btn btn-sm btn-outline-secondary ms-1">' + t.no + '</button>';

        form.parentNode.insertBefore(banner, form);

        banner.querySelectorAll('button')[0].addEventListener('click', function () {
            var raw = localStorage.getItem(key);
            if (raw) {
                try { restoreFormValues(form, JSON.parse(raw)); } catch (e) {}
            }
            banner.remove();
        });

        banner.querySelectorAll('button')[1].addEventListener('click', function () {
            localStorage.removeItem(key);
            banner.remove();
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('form[data-autosave-key]').forEach(function (form) {
            var key = 'form_autosave_' + form.dataset.autosaveKey;

            // Check for saved data and show restore prompt
            var saved = localStorage.getItem(key);
            if (saved) {
                try {
                    var data = JSON.parse(saved);
                    if (hasNonEmptyValues(data)) {
                        showRestoreBanner(form, key);
                    }
                } catch (e) {
                    localStorage.removeItem(key);
                }
            }

            // Save on every input/change (debounced)
            var save = debounce(function () {
                localStorage.setItem(key, JSON.stringify(getFormValues(form)));
            }, DEBOUNCE_MS);

            form.addEventListener('input', save);
            form.addEventListener('change', save);

            // Clear on successful submit
            form.addEventListener('submit', function () {
                localStorage.removeItem(key);
            });
        });
    });
})();
