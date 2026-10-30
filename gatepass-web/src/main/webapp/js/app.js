/*
 * Small browser-side helpers. They only make the forms friendlier: the SERVER (PassService)
 * checks every rule again, because anything in the browser can be switched off or changed.
 */
(function () {
    'use strict';

    // 1. Apply form: the expected return must be later than the exit.
    var form = document.getElementById('applyForm');
    if (form) {
        var exitInput = form.elements['exitDateTime'];
        var returnInput = form.elements['returnDateTime'];
        var message = document.getElementById('applyClientError');

        exitInput.addEventListener('change', function () {
            if (exitInput.value) {
                returnInput.min = exitInput.value; // date picker starts from the exit time
            }
        });

        form.addEventListener('submit', function (event) {
            message.hidden = true;
            if (exitInput.value && returnInput.value
                    && new Date(returnInput.value) <= new Date(exitInput.value)) {
                event.preventDefault();
                message.textContent = 'The expected return must be later than the exit time.';
                message.hidden = false;
                returnInput.focus();
            }
        });
    }

    // 2. Ask before rejecting a request (forms with a data-confirm text).
    document.querySelectorAll('form[data-confirm]').forEach(function (f) {
        f.addEventListener('submit', function (event) {
            if (!window.confirm(f.getAttribute('data-confirm'))) {
                event.preventDefault();
            }
        });
    });

    // 3. On narrow screens the tab bar scrolls sideways: keep the selected tab visible.
    var activeTab = document.querySelector('.tab.active');
    if (activeTab && activeTab.parentElement.scrollWidth > activeTab.parentElement.clientWidth) {
        activeTab.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }

    // 4. Disable the button after a click, so a double click cannot send the form twice.
    document.querySelectorAll('form').forEach(function (f) {
        f.addEventListener('submit', function (event) {
            if (event.defaultPrevented) {
                return;
            }
            var button = f.querySelector('button[type=submit]');
            if (button) {
                setTimeout(function () { button.disabled = true; }, 0);
            }
        });
    });
})();
