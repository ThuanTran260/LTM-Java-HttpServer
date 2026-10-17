// Web File Explorer Client Logic (Pure Standard JS)
(function () {
  'use strict';

  // RAM-only session state
  let csrfToken = null;
  let currentUser = null;
  let currentRole = null;
  let currentPath = '/';
  let currentParent = null;
  let currentOffset = 0;
  let currentTotal = 0;
  let fetchGeneration = 0;

  const PAGE_SIZE = 200;

  // Upload queue state
  const MAX_CONCURRENT_UPLOADS = 2;
  let activeUploads = 0;
  const uploadQueue = [];

  // DOM Elements
  const setupBanner = document.getElementById('setup-banner');
  const setupMessage = document.getElementById('setup-message');
  const statusRegion = document.getElementById('status-region');
  const loginView = document.getElementById('login-view');
  const loginForm = document.getElementById('login-form');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const explorerView = document.getElementById('explorer-view');
  const lblUser = document.getElementById('lbl-user');
  const lblRole = document.getElementById('lbl-role');
  const btnLogout = document.getElementById('btn-logout');
  const btnParent = document.getElementById('btn-parent');
  const btnRefresh = document.getElementById('btn-refresh');
  const crumbList = document.getElementById('crumb-list');
  const adminToolbar = document.getElementById('admin-toolbar');
  const newFolderInput = document.getElementById('new-folder-name');
  const btnMkdir = document.getElementById('btn-mkdir');
  const filePicker = document.getElementById('file-picker');
  const btnBrowse = document.getElementById('btn-browse');
  const dropZone = document.getElementById('drop-zone');
  const progressContainer = document.getElementById('progress-container');
  const fileListBody = document.getElementById('file-list-body');
  const emptyState = document.getElementById('empty-state');
  const btnPrevPage = document.getElementById('btn-prev-page');
  const btnNextPage = document.getElementById('btn-next-page');
  const pageInfo = document.getElementById('page-info');

  // Modal elements
  const modalOverlay = document.getElementById('modal-overlay');
  const modalTitle = document.getElementById('modal-title');
  const modalBody = document.getElementById('modal-body');
  const modalBtnCancel = document.getElementById('modal-btn-cancel');
  const modalBtnConfirm = document.getElementById('modal-btn-confirm');
  let currentModalConfirmHandler = null;

  // =========================================================================
  // UTILITY HELPERS
  // =========================================================================

  function showStatus(text, type) {
    statusRegion.textContent = text;
    statusRegion.className = 'status-region ' + (type || 'info');
  }

  function clearStatus() {
    statusRegion.textContent = '';
    statusRegion.className = 'status-region';
  }

  function formatBytes(bytes) {
    if (bytes === null || bytes === undefined) return '-';
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  function formatIsoDate(isoStr) {
    if (!isoStr) return '-';
    try {
      const d = new Date(isoStr);
      return d.toLocaleString('vi-VN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
    } catch (e) {
      return isoStr;
    }
  }

  function showModal(title, bodyNode, confirmText, confirmClass, onConfirm) {
    modalTitle.textContent = title;
    modalBody.innerHTML = '';
    if (typeof bodyNode === 'string') {
      const p = document.createElement('p');
      p.textContent = bodyNode;
      modalBody.appendChild(p);
    } else {
      modalBody.appendChild(bodyNode);
    }
    modalBtnConfirm.textContent = confirmText || 'Xác nhận';
    modalBtnConfirm.className = confirmClass || 'danger';
    currentModalConfirmHandler = onConfirm;
    modalOverlay.style.display = 'flex';
  }

  function hideModal() {
    modalOverlay.style.display = 'none';
    currentModalConfirmHandler = null;
    modalBody.innerHTML = '';
  }

  modalBtnCancel.addEventListener('click', hideModal);
  modalBtnConfirm.addEventListener('click', () => {
    if (typeof currentModalConfirmHandler === 'function') {
      currentModalConfirmHandler();
    }
    hideModal();
  });

  // =========================================================================
  // AUTHENTICATION & SESSION
  // =========================================================================

  async function checkSession() {
    clearStatus();
    setupBanner.style.display = 'none';
    try {
      const res = await fetch('/api/files/session', {
        method: 'GET',
        headers: { 'Cache-Control': 'no-store' }
      });

      if (res.status === 200) {
        const data = await res.json();
        onLoginSuccess(data);
      } else if (res.status === 503) {
        const data = await res.json().catch(() => ({}));
        showSetupRequired(data.message || 'Hệ thống chưa được thiết lập');
      } else {
        showLoginView();
      }
    } catch (err) {
      showLoginView();
    }
  }

  function showSetupRequired(msg) {
    setupBanner.style.display = 'block';
    setupMessage.textContent = msg;
    loginView.style.display = 'none';
    explorerView.style.display = 'none';
  }

  function showLoginView() {
    csrfToken = null;
    currentUser = null;
    currentRole = null;
    loginView.style.display = 'block';
    explorerView.style.display = 'none';
    setupBanner.style.display = 'none';
    usernameInput.value = '';
    passwordInput.value = '';
  }

  function onLoginSuccess(data) {
    csrfToken = data.csrfToken;
    currentUser = data.user;
    currentRole = data.role;

    lblUser.textContent = currentUser;
    lblRole.textContent = currentRole;
    lblRole.className = 'badge ' + (currentRole === 'ADMIN' ? 'badge-admin' : 'badge-guest');

    loginView.style.display = 'none';
    explorerView.style.display = 'block';
    setupBanner.style.display = 'none';

    // Toggle admin mutation tools
    if (currentRole === 'ADMIN') {
      adminToolbar.style.display = 'flex';
      dropZone.style.display = 'block';
    } else {
      adminToolbar.style.display = 'none';
      dropZone.style.display = 'none';
    }

    loadDirectory(currentPath);
  }

  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearStatus();
    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    try {
      const res = await fetch('/api/files/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-File-Explorer': '1'
        },
        body: JSON.stringify({ username, password })
      });

      const data = await res.json();
      if (res.status === 200) {
        onLoginSuccess(data);
      } else if (res.status === 503) {
        showSetupRequired(data.message);
      } else if (res.status === 429) {
        showStatus('Đăng nhập thất bại quá 5 lần. Vui lòng thử lại sau 60 giây.', 'error');
      } else {
        showStatus(data.message || 'Tên người dùng hoặc mật khẩu không đúng.', 'error');
      }
    } catch (err) {
      showStatus('Không thể kết nối đến máy chủ: ' + err.message, 'error');
    }
  });

  btnLogout.addEventListener('click', async () => {
    try {
      const res = await fetch('/api/files/logout', {
        method: 'POST',
        headers: {
          'X-File-Explorer': '1',
          'X-CSRF-Token': csrfToken || ''
        }
      });

      if (res.status === 204) {
        showLoginView();
        showStatus('Đã đăng xuất.', 'info');
      } else if (res.status === 401) {
        showLoginView();
        showStatus('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.', 'error');
      } else {
        showStatus('Đăng xuất thất bại. Vui lòng thử lại.', 'error');
      }
    } catch (err) {
      showStatus('Không thể kết nối đến máy chủ: ' + err.message, 'error');
    }
  });

  // =========================================================================
  // DIRECTORY LISTING & NAVIGATION
  // =========================================================================

  async function loadDirectory(path, offset = 0) {
    const gen = ++fetchGeneration;
    clearStatus();
    btnPrevPage.disabled = true;
    btnNextPage.disabled = true;

    try {
      const query = new URLSearchParams({ path, offset: String(offset), limit: String(PAGE_SIZE) });
      // URLSearchParams encodes spaces as '+', which the server query decoder reads literally.
      const res = await fetch('/api/files?' + query.toString().replace(/\+/g, '%20'), {
        method: 'GET',
        headers: { 'Cache-Control': 'no-store' }
      });

      if (gen !== fetchGeneration) return false; // Stale request, ignore

      if (res.status === 401) {
        updatePageButtons(currentOffset, currentTotal);
        showLoginView();
        showStatus('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.', 'error');
        return false;
      }

      if (res.status === 503) {
        const data = await res.json().catch(() => ({}));
        if (gen !== fetchGeneration) return false;
        updatePageButtons(currentOffset, currentTotal);
        showSetupRequired(data.message);
        return false;
      }

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        if (gen !== fetchGeneration) return false;
        showStatus('Lỗi tải danh mục: ' + (errData.message || res.statusText), 'error');
        updatePageButtons(currentOffset, currentTotal);
        return false;
      }

      const page = await res.json();
      if (gen !== fetchGeneration) return false;

      if (page.total > 0 && page.offset >= page.total) {
        // A deletion (or concurrent change) left this page out of range; reload the
        // last page that still has entries. The corrected offset always decreases,
        // so this retries at most once per vanished page.
        return loadDirectory(path, Math.floor((page.total - 1) / PAGE_SIZE) * PAGE_SIZE);
      }

      currentPath = page.path;
      currentParent = page.parent;
      currentOffset = page.offset;
      currentTotal = page.total;

      renderBreadcrumbs(currentPath, currentParent);
      renderTable(page.entries || []);
      renderPageInfo(page);
      return true;
    } catch (err) {
      if (gen === fetchGeneration) {
        showStatus('Lỗi kết nối máy chủ: ' + err.message, 'error');
        updatePageButtons(currentOffset, currentTotal);
      }
      return false;
    }
  }

  function renderBreadcrumbs(path, parent) {
    crumbList.innerHTML = '';
    btnParent.disabled = (parent === null);

    const parts = path.split('/').filter(p => p.length > 0);

    // Root crumb
    const rootSpan = document.createElement('span');
    if (parts.length === 0) {
      rootSpan.textContent = '/ (Gốc)';
      rootSpan.className = 'crumb-current';
    } else {
      const rootLink = document.createElement('a');
      rootLink.textContent = '/ (Gốc)';
      rootLink.className = 'crumb-link';
      rootLink.addEventListener('click', () => loadDirectory('/'));
      rootSpan.appendChild(rootLink);
    }
    crumbList.appendChild(rootSpan);

    let accum = '';
    for (let i = 0; i < parts.length; i++) {
      accum += '/' + parts[i];
      const sep = document.createElement('span');
      sep.textContent = ' / ';
      crumbList.appendChild(sep);

      const span = document.createElement('span');
      if (i === parts.length - 1) {
        span.textContent = parts[i];
        span.className = 'crumb-current';
      } else {
        const dirTarget = accum;
        const link = document.createElement('a');
        link.textContent = parts[i];
        link.className = 'crumb-link';
        link.addEventListener('click', () => loadDirectory(dirTarget));
        span.appendChild(link);
      }
      crumbList.appendChild(span);
    }
  }

  btnParent.addEventListener('click', () => {
    if (currentParent !== null) {
      loadDirectory(currentParent);
    }
  });

  btnRefresh.addEventListener('click', () => {
    loadDirectory(currentPath, currentOffset);
  });

  btnPrevPage.addEventListener('click', () => {
    loadDirectory(currentPath, currentOffset - PAGE_SIZE);
  });

  btnNextPage.addEventListener('click', () => {
    loadDirectory(currentPath, currentOffset + PAGE_SIZE);
  });

  function renderTable(entries) {
    fileListBody.innerHTML = '';

    if (!entries || entries.length === 0) {
      emptyState.style.display = 'block';
      return;
    }
    emptyState.style.display = 'none';

    entries.forEach(e => {
      const tr = document.createElement('tr');

      // 1. Name & Icon
      const tdName = document.createElement('td');
      const icon = document.createElement('span');
      icon.style.marginRight = '8px';
      icon.textContent = e.directory ? '📁' : '📄';
      tdName.appendChild(icon);

      if (e.blocked) {
        const nameSpan = document.createElement('span');
        nameSpan.textContent = e.name;
        nameSpan.className = 'item-blocked';
        tdName.appendChild(nameSpan);
      } else if (e.directory) {
        const link = document.createElement('a');
        link.textContent = e.name;
        link.className = 'item-link';
        link.addEventListener('click', () => loadDirectory(e.path));
        tdName.appendChild(link);
      } else {
        const textNode = document.createTextNode(e.name);
        tdName.appendChild(textNode);
      }
      tr.appendChild(tdName);

      // 2. Type
      const tdType = document.createElement('td');
      if (e.blocked) {
        tdType.textContent = 'Bị chặn (Link/Junction)';
        tdType.className = 'item-blocked';
      } else {
        tdType.textContent = e.directory ? 'Thư mục' : 'Tệp tin';
      }
      tr.appendChild(tdType);

      // 3. Size
      const tdSize = document.createElement('td');
      tdSize.textContent = e.directory ? '-' : formatBytes(e.size);
      tr.appendChild(tdSize);

      // 4. Modified
      const tdMod = document.createElement('td');
      tdMod.textContent = formatIsoDate(e.modified);
      tr.appendChild(tdMod);

      // 5. Actions
      const tdActions = document.createElement('td');
      if (!e.blocked) {
        if (!e.directory) {
          // Download action (direct link)
          const btnDl = document.createElement('button');
          btnDl.type = 'button';
          btnDl.className = 'secondary sm';
          btnDl.textContent = 'Tải về';
          btnDl.addEventListener('click', () => {
            window.location.href = '/api/files/download?path=' + encodeURIComponent(e.path);
          });
          tdActions.appendChild(btnDl);
        }

        if (currentRole === 'ADMIN') {
          // Rename button
          const btnRen = document.createElement('button');
          btnRen.type = 'button';
          btnRen.className = 'secondary sm';
          btnRen.textContent = 'Đổi tên';
          btnRen.addEventListener('click', () => promptRename(e));
          tdActions.appendChild(btnRen);

          // Delete button
          const btnDel = document.createElement('button');
          btnDel.type = 'button';
          btnDel.className = 'danger sm';
          btnDel.textContent = 'Xóa';
          btnDel.addEventListener('click', () => promptDelete(e));
          tdActions.appendChild(btnDel);
        }
      } else {
        tdActions.textContent = '-';
      }
      tr.appendChild(tdActions);

      fileListBody.appendChild(tr);
    });
  }

  function updatePageButtons(offset, total) {
    btnPrevPage.disabled = offset <= 0;
    btnNextPage.disabled = offset + PAGE_SIZE >= total;
  }

  function renderPageInfo(page) {
    if (page.total === 0) {
      pageInfo.textContent = '0 mục';
    } else {
      const entries = page.entries || [];
      pageInfo.textContent = (page.offset + 1) + '–' +
        Math.min(page.offset + entries.length, page.total) + ' / ' + page.total;
    }
    updatePageButtons(page.offset, page.total);
  }

  // =========================================================================
  // FILE MUTATIONS (MKDIR, RENAME, DELETE)
  // =========================================================================

  btnMkdir.addEventListener('click', async () => {
    const dirName = newFolderInput.value.trim();
    if (!dirName) {
      showStatus('Vui lòng nhập tên thư mục mới.', 'error');
      return;
    }

    const targetVirtual = currentPath + (currentPath.endsWith('/') ? '' : '/') + dirName;
    try {
      const res = await fetch('/api/files/mkdir', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-File-Explorer': '1',
          'X-CSRF-Token': csrfToken
        },
        body: JSON.stringify({ path: targetVirtual })
      });

      if (res.status === 201) {
        newFolderInput.value = '';
        showStatus('Đã tạo thư mục thành công.', 'success');
        loadDirectory(currentPath);
      } else {
        const data = await res.json().catch(() => ({}));
        showStatus('Tạo thư mục thất bại: ' + (data.message || res.statusText), 'error');
      }
    } catch (err) {
      showStatus('Lỗi kết nối máy chủ: ' + err.message, 'error');
    }
  });

  function promptRename(entry) {
    const wrap = document.createElement('div');
    const p = document.createElement('p');
    p.textContent = 'Nhập tên mới cho: ';
    const strong = document.createElement('strong');
    strong.textContent = entry.name;
    p.appendChild(strong);
    wrap.appendChild(p);

    const input = document.createElement('input');
    input.type = 'text';
    input.value = entry.name;
    input.style.width = '100%';
    input.style.boxSizing = 'border-box';
    wrap.appendChild(input);

    showModal('Đổi tên ' + (entry.directory ? 'thư mục' : 'tệp tin'), wrap, 'Đổi tên', 'secondary', async () => {
      const newName = input.value.trim();
      if (!newName || newName === entry.name) return;

      const parentPath = entry.path.substring(0, entry.path.lastIndexOf('/')) || '/';
      const toVirtual = parentPath + (parentPath.endsWith('/') ? '' : '/') + newName;

      try {
        const res = await fetch('/api/files/rename', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-File-Explorer': '1',
            'X-CSRF-Token': csrfToken
          },
          body: JSON.stringify({ from: entry.path, to: toVirtual })
        });

        if (res.status === 200) {
          showStatus('Đã đổi tên thành công.', 'success');
          loadDirectory(currentPath);
        } else {
          const data = await res.json().catch(() => ({}));
          showStatus('Đổi tên thất bại: ' + (data.message || res.statusText), 'error');
        }
      } catch (err) {
        showStatus('Lỗi kết nối máy chủ: ' + err.message, 'error');
      }
    });

    setTimeout(() => input.focus(), 50);
  }

  function promptDelete(entry) {
    const wrap = document.createElement('div');
    const p = document.createElement('p');
    p.textContent = 'Bạn có chắc chắn muốn xóa ' + (entry.directory ? 'thư mục' : 'tệp tin') + ': ';
    const strong = document.createElement('strong');
    strong.textContent = entry.name;
    p.appendChild(strong);
    p.appendChild(document.createTextNode('?'));
    wrap.appendChild(p);

    if (entry.directory) {
      const note = document.createElement('p');
      note.style.fontSize = '12px';
      note.style.color = '#dc2626';
      note.textContent = 'Lưu ý: Chỉ có thể xóa thư mục rỗng. Không hỗ trợ xóa đệ quy.';
      wrap.appendChild(note);
    }

    showModal('Xác nhận xóa', wrap, 'Xóa vĩnh viễn', 'danger', async () => {
      try {
        const res = await fetch('/api/files?path=' + encodeURIComponent(entry.path), {
          method: 'DELETE',
          headers: {
            'X-File-Explorer': '1',
            'X-CSRF-Token': csrfToken
          }
        });

        if (res.status === 204) {
          showStatus('Đã xóa thành công.', 'success');
          loadDirectory(currentPath, currentOffset);
        } else {
          const data = await res.json().catch(() => ({}));
          showStatus('Xóa thất bại: ' + (data.message || res.statusText), 'error');
        }
      } catch (err) {
        showStatus('Lỗi kết nối máy chủ: ' + err.message, 'error');
      }
    });
  }

  // =========================================================================
  // FILE UPLOAD WITH XHR & QUEUE (MAX 2 CONCURRENT)
  // =========================================================================

  btnBrowse.addEventListener('click', () => {
    filePicker.click();
  });

  filePicker.addEventListener('change', () => {
    if (filePicker.files && filePicker.files.length > 0) {
      enqueueFiles(filePicker.files);
      filePicker.value = '';
    }
  });

  // Drag and drop handling
  ['dragenter', 'dragover'].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.add('dragover');
    });
  });

  ['dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.remove('dragover');
    });
  });

  dropZone.addEventListener('drop', (e) => {
    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      enqueueFiles(e.dataTransfer.files);
    }
  });

  dropZone.addEventListener('click', () => {
    filePicker.click();
  });

  dropZone.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      filePicker.click();
    }
  });

  function enqueueFiles(files) {
    if (currentRole !== 'ADMIN') {
      showStatus('Tài khoản của bạn không có quyền tải lên.', 'error');
      return;
    }

    for (let i = 0; i < files.length; i++) {
      uploadQueue.push({
        file: files[i],
        targetDir: currentPath
      });
    }

    processUploadQueue();
  }

  function processUploadQueue() {
    while (activeUploads < MAX_CONCURRENT_UPLOADS && uploadQueue.length > 0) {
      const item = uploadQueue.shift();
      startUpload(item.file, item.targetDir);
    }
  }

  function startUpload(file, targetDir) {
    activeUploads++;

    // Create progress item in DOM
    const itemDiv = document.createElement('div');
    itemDiv.className = 'progress-item';

    const nameSpan = document.createElement('span');
    nameSpan.style.width = '180px';
    nameSpan.style.overflow = 'hidden';
    nameSpan.style.textOverflow = 'ellipsis';
    nameSpan.style.whiteSpace = 'nowrap';
    nameSpan.textContent = file.name;
    itemDiv.appendChild(nameSpan);

    const barBg = document.createElement('div');
    barBg.className = 'progress-bar-bg';
    const barFill = document.createElement('div');
    barFill.className = 'progress-bar-fill';
    barBg.appendChild(barFill);
    itemDiv.appendChild(barBg);

    const pctSpan = document.createElement('span');
    pctSpan.style.width = '45px';
    pctSpan.style.textAlign = 'right';
    pctSpan.textContent = '0%';
    itemDiv.appendChild(pctSpan);

    const btnCancel = document.createElement('button');
    btnCancel.type = 'button';
    btnCancel.className = 'secondary sm';
    btnCancel.textContent = 'Hủy';
    itemDiv.appendChild(btnCancel);

    progressContainer.appendChild(itemDiv);

    const targetVirtual = targetDir + (targetDir.endsWith('/') ? '' : '/') + file.name;
    const xhr = new XMLHttpRequest();
    let isCancelled = false;

    btnCancel.addEventListener('click', () => {
      isCancelled = true;
      xhr.abort();
    });

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        const pct = Math.floor((e.loaded / e.total) * 100);
        barFill.style.width = pct + '%';
        pctSpan.textContent = pct + '%';
      }
    };

    xhr.onload = () => {
      activeUploads--;
      btnCancel.remove();

      if (xhr.status === 201) {
        barFill.style.backgroundColor = '#16a34a';
        pctSpan.textContent = '100%';
        showStatus('Tải lên thành công: ' + file.name, 'success');
        if (currentPath === targetDir) {
          loadDirectory(currentPath);
        }
      } else {
        barFill.style.backgroundColor = '#dc2626';
        let errMsg = 'Mã lỗi ' + xhr.status;
        try {
          const resp = JSON.parse(xhr.responseText);
          if (resp.message) errMsg = resp.message;
        } catch (ignored) {}

        if (xhr.status === 409) {
          showStatus('Tệp đã tồn tại (không hỗ trợ ghi đè qua Web): ' + file.name, 'error');
        } else {
          showStatus('Tải lên thất bại (' + file.name + '): ' + errMsg, 'error');
        }
      }

      setTimeout(() => itemDiv.remove(), 4000);
      processUploadQueue();
    };

    xhr.onerror = () => {
      activeUploads--;
      btnCancel.remove();
      barFill.style.backgroundColor = '#dc2626';
      showStatus('Lỗi mạng khi tải lên ' + file.name + '. Vui lòng làm mới danh sách để kiểm tra trạng thái.', 'error');
      setTimeout(() => itemDiv.remove(), 4000);
      processUploadQueue();
    };

    xhr.onabort = () => {
      activeUploads--;
      btnCancel.remove();
      barFill.style.backgroundColor = '#64748b';
      pctSpan.textContent = 'Đã hủy';
      showStatus('Đã hủy tải lên: ' + file.name, 'info');
      setTimeout(() => itemDiv.remove(), 2500);
      processUploadQueue();
    };

    xhr.open('PUT', '/api/files/content?path=' + encodeURIComponent(targetVirtual), true);
    xhr.setRequestHeader('X-File-Explorer', '1');
    xhr.setRequestHeader('X-CSRF-Token', csrfToken || '');
    xhr.setRequestHeader('X-File-Size', String(file.size));
    xhr.send(file);
  }

  // =========================================================================
  // INITIALIZATION
  // =========================================================================

  checkSession();
})();
