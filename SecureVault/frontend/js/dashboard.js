const API_BASE = `http://${window.location.hostname}:8080`;
const fileList = document.querySelector('#file-list');
const pageMessage = document.querySelector('#page-message');
const encryptForm = document.querySelector('#encrypt-form');
const fileInput = document.querySelector('#file-input');
const decryptDialog = document.querySelector('#decrypt-dialog');
const decryptForm = document.querySelector('#decrypt-form');
const decryptMessage = document.querySelector('#decrypt-message');
let selectedFile = null;

async function responseMessage(response) {
  const body = await response.json().catch(() => ({}));
  return body.message || `Request failed (${response.status}). Please try again.`;
}

function setMessage(element, message, type = 'error') {
  element.textContent = message;
  element.className = `form-message ${type}`;
  element.hidden = false;
}

function clearMessage(element) {
  element.hidden = true;
  element.textContent = '';
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let size = bytes;
  let unit = -1;
  do {
    size /= 1024;
    unit += 1;
  } while (size >= 1024 && unit < units.length - 1);
  return `${size.toFixed(size < 10 ? 1 : 0)} ${units[unit]}`;
}

function formatDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Unknown' : new Intl.DateTimeFormat(undefined, {
    year: 'numeric', month: 'short', day: 'numeric'
  }).format(date);
}

async function loadAccount() {
  const response = await fetch(`${API_BASE}/api/auth/me`, { credentials: 'include' });
  if (!response.ok) {
    window.location.replace('index.html');
    return false;
  }
  const account = await response.json();
  document.querySelector('#account-name').textContent = account.name;
  document.querySelector('#account-avatar').textContent = account.name.trim().charAt(0).toUpperCase() || 'S';
  return true;
}

function makeAction(label, className, handler) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = `action-button ${className}`.trim();
  button.textContent = label;
  button.addEventListener('click', handler);
  return button;
}

function makeFileRow(file) {
  const row = document.createElement('tr');
  const nameCell = document.createElement('td');
  const fileInfo = document.createElement('div');
  const fileIcon = document.createElement('span');
  const fileName = document.createElement('span');
  fileInfo.className = 'file-cell';
  fileIcon.className = 'file-icon';
  const extension = file.originalFilename.includes('.') ? file.originalFilename.split('.').pop().slice(0, 4) : 'FILE';
  fileIcon.textContent = extension.toUpperCase();
  fileIcon.setAttribute('aria-hidden', 'true');
  fileName.className = 'file-cell-name';
  fileName.textContent = file.originalFilename;
  fileInfo.append(fileIcon, fileName);
  nameCell.append(fileInfo);

  const sizeCell = document.createElement('td');
  sizeCell.textContent = formatSize(file.fileSize);
  const algorithmCell = document.createElement('td');
  const tag = document.createElement('span');
  tag.className = 'algorithm-tag';
  tag.textContent = file.encryptionAlgorithm;
  algorithmCell.append(tag);
  const dateCell = document.createElement('td');
  dateCell.textContent = formatDate(file.uploadedAt);
  const actionsCell = document.createElement('td');
  const actions = document.createElement('div');
  actions.className = 'row-actions';
  actions.append(
    makeAction('Download', '', () => downloadEncrypted(file)),
    makeAction('Decrypt', '', () => openDecryptDialog(file)),
    makeAction('Delete', 'delete-action', () => deleteFile(file))
  );
  actionsCell.append(actions);
  row.append(nameCell, sizeCell, algorithmCell, dateCell, actionsCell);
  return row;
}

async function loadFiles() {
  fileList.replaceChildren();
  const loading = document.createElement('tr');
  const cell = document.createElement('td');
  cell.className = 'table-state';
  cell.colSpan = 5;
  cell.textContent = 'Loading your files...';
  loading.append(cell);
  fileList.append(loading);
  document.querySelector('#empty-files').hidden = true;
  try {
    const response = await fetch(`${API_BASE}/api/files`, { credentials: 'include' });
    if (!response.ok) throw new Error(await responseMessage(response));
    const files = await response.json();
    fileList.replaceChildren();
    if (files.length === 0) {
      document.querySelector('#empty-files').hidden = false;
      fileList.closest('.table-wrap').hidden = true;
      return;
    }
    fileList.closest('.table-wrap').hidden = false;
    files.forEach((file) => fileList.append(makeFileRow(file)));
  } catch (error) {
    fileList.replaceChildren();
    const errorRow = document.createElement('tr');
    const errorCell = document.createElement('td');
    errorCell.className = 'table-state';
    errorCell.colSpan = 5;
    errorCell.textContent = error.message;
    errorRow.append(errorCell);
    fileList.append(errorRow);
  }
}

async function downloadEncrypted(file) {
  try {
    const response = await fetch(`${API_BASE}/api/files/${file.id}/download`, { credentials: 'include' });
    if (!response.ok) throw new Error(await responseMessage(response));
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = `securevault-${file.id}.enc`;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch (error) {
    setMessage(pageMessage, error.message);
  }
}

function openDecryptDialog(file) {
  selectedFile = file;
  document.querySelector('#decrypt-filename').textContent = file.originalFilename;
  decryptForm.elements.password.value = '';
  clearMessage(decryptMessage);
  decryptDialog.showModal();
  document.querySelector('#decrypt-password').focus();
}

async function deleteFile(file) {
  if (!window.confirm(`Delete the encrypted copy of "${file.originalFilename}"? This cannot be undone.`)) return;
  try {
    const response = await fetch(`${API_BASE}/api/files/${file.id}`, {
      method: 'DELETE',
      credentials: 'include'
    });
    if (!response.ok) throw new Error(await responseMessage(response));
    setMessage(pageMessage, 'Encrypted file deleted.', 'success');
    await loadFiles();
  } catch (error) {
    setMessage(pageMessage, error.message);
  }
}

fileInput.addEventListener('change', () => {
  document.querySelector('#file-name').textContent = fileInput.files[0]?.name || 'Choose a file';
});

encryptForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const file = fileInput.files[0];
  if (!file) return;
  if (file.size > 100 * 1024 * 1024) {
    setMessage(pageMessage, 'Files must be 100 MB or smaller.');
    return;
  }
  if (new TextEncoder().encode(encryptForm.elements.password.value).length > 72) {
    setMessage(pageMessage, 'The encryption password must be no more than 72 UTF-8 bytes.');
    return;
  }
  const button = document.querySelector('#encrypt-button');
  const originalLabel = button.innerHTML;
  button.disabled = true;
  button.textContent = 'Encrypting...';
  clearMessage(pageMessage);
  try {
    const data = new FormData();
    data.append('file', file);
    data.append('password', encryptForm.elements.password.value);
    const response = await fetch(`${API_BASE}/api/files/encrypt`, {
      method: 'POST',
      credentials: 'include',
      body: data
    });
    if (!response.ok) throw new Error(await responseMessage(response));
    encryptForm.reset();
    document.querySelector('#file-name').textContent = 'Choose a file';
    setMessage(pageMessage, 'Your file is encrypted and stored.', 'success');
    await loadFiles();
  } catch (error) {
    setMessage(pageMessage, error.message || 'The file could not be encrypted.');
  } finally {
    button.disabled = false;
    button.innerHTML = originalLabel;
  }
});

decryptForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!selectedFile) return;
  const button = document.querySelector('#decrypt-button');
  button.disabled = true;
  clearMessage(decryptMessage);
  try {
    const data = new URLSearchParams({ password: decryptForm.elements.password.value });
    const response = await fetch(`${API_BASE}/api/files/${selectedFile.id}/decrypt`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: data
    });
    if (!response.ok) throw new Error(await responseMessage(response));
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = selectedFile.originalFilename;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    decryptDialog.close();
    setMessage(pageMessage, 'File decrypted and downloaded.', 'success');
  } catch (error) {
    setMessage(decryptMessage, error.message || 'The file could not be decrypted.');
  } finally {
    button.disabled = false;
  }
});

document.querySelector('#logout-button').addEventListener('click', async () => {
  try {
    await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' });
  } finally {
    window.location.replace('index.html');
  }
});
document.querySelector('#refresh-button').addEventListener('click', loadFiles);
document.querySelector('#close-dialog').addEventListener('click', () => decryptDialog.close());
document.querySelector('#cancel-decrypt').addEventListener('click', () => decryptDialog.close());

loadAccount().then((signedIn) => signedIn && loadFiles()).catch(() => {
  setMessage(pageMessage, 'Could not reach SecureVault. Check that the backend is running.');
});