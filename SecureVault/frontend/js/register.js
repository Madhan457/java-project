const API_BASE = `http://${window.location.hostname}:8080`;
const registerForm = document.querySelector('#register-form');
const messageBox = document.querySelector('#form-message');

function showMessage(message, type = 'error') {
  messageBox.textContent = message;
  messageBox.className = `form-message ${type}`;
  messageBox.hidden = false;
}

registerForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const password = registerForm.elements.password.value;
  const confirmPassword = registerForm.elements['confirm-password'].value;
  if (password !== confirmPassword) {
    showMessage('The passwords do not match.');
    return;
  }
  if (new TextEncoder().encode(password).length > 72) {
    showMessage('Your password must be no more than 72 UTF-8 bytes.');
    return;
  }

  const button = registerForm.querySelector('button[type="submit"]');
  button.disabled = true;
  messageBox.hidden = true;
  try {
    const response = await fetch(`${API_BASE}/api/auth/register`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: registerForm.elements.name.value.trim(),
        email: registerForm.elements.email.value.trim(),
        password
      })
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || 'Unable to create the account. Check your details and try again.');
    showMessage('Account created. Taking you to sign in...', 'success');
    window.setTimeout(() => window.location.assign('index.html'), 900);
  } catch (error) {
    showMessage(error.message || 'Could not reach SecureVault. Check that the backend is running.');
  } finally {
    button.disabled = false;
  }
});