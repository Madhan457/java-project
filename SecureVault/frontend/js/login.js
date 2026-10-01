const API_BASE = `http://${window.location.hostname}:8080`;
const loginForm = document.querySelector('#login-form');
const messageBox = document.querySelector('#form-message');

function showMessage(message, type = 'error') {
  messageBox.textContent = message;
  messageBox.className = `form-message ${type}`;
  messageBox.hidden = false;
}

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = loginForm.querySelector('button[type="submit"]');
  button.disabled = true;
  messageBox.hidden = true;

  try {
    const response = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: loginForm.elements.email.value.trim(),
        password: loginForm.elements.password.value
      })
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || 'Unable to sign in. Check your details and try again.');
    window.location.assign('dashboard.html');
  } catch (error) {
    showMessage(error.message || 'Could not reach SecureVault. Check that the backend is running.');
  } finally {
    button.disabled = false;
  }
});