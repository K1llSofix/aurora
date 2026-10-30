import { h, fill, btn, iconBtn, modal, modalHead, popover, toast, head, clear, forgetFaces } from './dom.js';
import { skinEditor } from './skin-editor.js';
import { icon } from './icons.js';
import { t } from './i18n.js';
import { api, state, set, currentAccount, showError } from './store.js';

const NAME = /^[A-Za-z0-9_]{3,16}$/;

function typeLabel(account) {
  if (account.expired) return t('accounts.expired');
  return account.type === 'microsoft' ? t('accounts.typeMicrosoft') : t('accounts.typeOffline');
}

/** The account card at the bottom of the sidebar. */
export function accountCard() {
  const el = h('button.account-card', { type: 'button' });
  const render = () => {
    clear(el);
    const account = currentAccount();
    if (!account) {
      el.append(
        h('div.head', icon('user')),
        h('div.grow', h('div.name', t('accounts.none')), h('div.status', t('accounts.noneSub'))),
        icon('plus'),
      );
      el.lastChild.classList.add('chev');
      return;
    }
    el.append(
      head(account),
      h('div.grow', { style: { minWidth: 0 } },
        h('div.name.ellipsis', account.name),
        h('div.status', h(`span.dot.${account.expired ? 'warn' : account.type === 'microsoft' ? 'ok' : ''}`), typeLabel(account))),
      icon('chevronUpDown'),
    );
    el.lastChild.classList.add('chev');
  };
  el.addEventListener('click', () => {
    if (!state.accounts.length) addAccountDialog();
    else accountMenu(el);
  });
  render();
  return { el, render };
}

function accountMenu(anchor) {
  popover(anchor, (close) => [
    h('div.menu-label', t('accounts.title')),
    h('div.menu-scroll', state.accounts.map((a) => h('div.menu-item', {
      role: 'button',
      tabindex: 0,
      onclick: async () => {
        close();
        if (a.selected) return;
        try {
          await api.accounts.select(a.id);
          toast(t('accounts.switched', { name: a.name }), { icon: 'user' });
        } catch (e) {
          showError(e);
        }
      },
    },
    head(a, 'sm'),
    h('div.grow', { style: { minWidth: 0 } }, h('div.ellipsis', a.name), h('div.sub', typeLabel(a))),
    a.selected ? h('span.check', icon('check')) : null,
    a.type === 'offline' ? iconBtn('palette', {
      tip: t('skin.edit'),
      onclick: (e) => {
        e.stopPropagation();
        close();
        editSkin(a);
      },
    }) : null,
    iconBtn('trash', {
      class: 'danger',
      tip: t('accounts.remove'),
      onclick: async (e) => {
        e.stopPropagation();
        close();
        try {
          await api.accounts.remove(a.id);
          toast(t('accounts.removed', { name: a.name }), { icon: 'trash' });
        } catch (err) {
          showError(err);
        }
      },
    })))),
    h('div.menu-sep'),
    h('button.menu-item', { type: 'button', onclick: () => { close(); addAccountDialog(); } }, icon('plus'), h('div.grow', t('accounts.add'))),
  ], { above: true, minWidth: 280 });
}

/** Opens the skin editor; afterwards every head is redrawn with the new skin. */
export function editSkin(account) {
  skinEditor(account, async () => {
    forgetFaces();
    set('accounts', await api.accounts.list());
  });
}

/** Picks the kind of account to add. An offline name asks for a Microsoft sign-in first. */
export async function addAccountDialog() {
  const unlocked = await api.accounts.offlineUnlocked().catch(() => false);
  modal((close) => [
    modalHead(t('accounts.addTitle'), t('accounts.addSubtitle'), close),
    h('div.modal-body',
      h('div.choice-grid',
        h('button.choice', { type: 'button', onclick: () => { close(); signInDialog(); } },
          h('div.ic', icon('microsoft')), h('div.t', t('accounts.microsoft')), h('div.d', t('accounts.microsoftSub'))),
        h('button.choice', { type: 'button', onclick: () => { close(); if (unlocked) offlineDialog(); else signInDialog(true); } },
          h('div.ic', icon('user')), h('div.t', t('accounts.offline')), h('div.d', t(unlocked ? 'accounts.offlineSub' : 'accounts.offlineLocked'))))),
    h('div.modal-foot'),
  ]);
}

/** {@code unapproved}: the player just signed in, and Mojang has not approved the launcher. */
function offlineDialog(unapproved = false) {
  modal((close) => {
    const input = h('input.input', { placeholder: t('accounts.namePlaceholder'), maxlength: 16, spellcheck: false });
    const hint = h('div.hint', t('accounts.nameHint'));
    const add = btn(t('common.add'), { class: 'primary', icon: 'plus', disabled: true });
    const check = () => {
      const ok = NAME.test(input.value.trim());
      add.disabled = !ok;
      input.classList.toggle('invalid', Boolean(input.value) && !ok);
      return ok;
    };
    const submit = async () => {
      if (!check()) return;
      add.disabled = true;
      try {
        await api.accounts.addOffline(input.value.trim());
        toast(t('accounts.added', { name: input.value.trim() }), { icon: 'user' });
        close();
      } catch (e) {
        showError(e);
        add.disabled = false;
      }
    };
    input.addEventListener('input', check);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') submit();
    });
    add.addEventListener('click', submit);
    return [
      modalHead(t('accounts.offlineTitle'), null, close),
      h('div.modal-body',
        unapproved ? h('div.note', t('accounts.msUnapproved')) : null,
        h('div.field', h('label', t('accounts.name')), h('div.input-wrap', icon('user'), input), hint),
        h('div.note', t('accounts.offlineNote'))),
      h('div.modal-foot', btn(t('common.cancel'), { onclick: () => close() }), add),
    ];
  });
}

/** Microsoft sign-in with a device code typed on microsoft.com; then the offline form if asked for. */
function signInDialog(thenOffline = false) {
  let unsubscribe = null;
  let finished = false;
  const dialog = modal((close) => {
    const body = h('div.modal-body', h('div.device-code', h('div.spinner.lg'), h('div.muted', t('accounts.msRequesting'))));
    unsubscribe = api.accounts.onCode(({ code, url }) => {
      fill(body, h('div.device-code',
        h('div.muted', t('accounts.msEnter')),
        h('div.code', code),
        h('div.row',
          btn(t('accounts.msOpen', { url: url.replace(/^https?:\/\//, '') }), {
            class: 'primary',
            icon: 'external',
            onclick: () => {
              api.app.copy(code);
              api.app.openExternal(url);
            },
          }),
          btn(t('accounts.msCopy'), {
            icon: 'copy',
            onclick: () => {
              api.app.copy(code);
              toast(t('common.copied'), { icon: 'copy' });
            },
          })),
        h('div.wait', h('div.spinner'), t('accounts.msWaiting')),
        h('div.note', h('span', icon('shield')), t('accounts.msPrivacy'))));
    });
    return [modalHead(t('accounts.msTitle'), null, close), body, h('div.modal-foot', btn(t('common.cancel'), { onclick: () => close() }))];
  }, {
    onClose: () => {
      unsubscribe?.();
      if (!finished) api.accounts.cancelSignIn().catch(() => {});
    },
  });
  api.accounts.signIn().then((id) => {
    finished = true;
    dialog.close();
    if (id) {
      const account = state.accounts.find((a) => a.id === id);
      toast(t('accounts.msDone', { name: account ? account.name : '' }), { icon: 'check' });
    }
    if (!id || thenOffline) offlineDialog(!id);
  }).catch((e) => {
    finished = true;
    if (e.info && e.info.code === 'cancelled') return;
    dialog.close();
    showError(e);
  });
}
