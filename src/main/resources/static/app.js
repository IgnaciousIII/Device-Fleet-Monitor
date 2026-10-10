const refreshIntervalMs = 5000;
const elements = {
  alert: document.querySelector('#alert'),
  connection: document.querySelector('.connection'),
  connectionLabel: document.querySelector('#connection-label'),
  total: document.querySelector('#total-count'),
  online: document.querySelector('#online-count'),
  offline: document.querySelector('#offline-count'),
  rows: document.querySelector('#device-rows'),
  tableMessage: document.querySelector('#table-message'),
  lastUpdated: document.querySelector('#last-updated'),
  filter: document.querySelector('#status-filter'),
  search: document.querySelector('#search-devices'),
  refreshButton: document.querySelector('#refresh-button'),
  registerForm: document.querySelector('#register-form'),
  registerButton: document.querySelector('#register-button'),
  formMessage: document.querySelector('#form-message'),
};

let devices = [];
let isLoading = false;

function showAlert(message) {
  elements.alert.textContent = message;
  elements.alert.hidden = false;
}

function clearAlert() {
  elements.alert.textContent = '';
  elements.alert.hidden = true;
}

async function readResponse(response) {
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.message || `Request failed (${response.status})`);
  }
  return body;
}

function setConnection(connected) {
  elements.connection.classList.toggle('is-connected', connected);
  elements.connection.classList.toggle('is-disconnected', !connected);
  elements.connectionLabel.textContent = connected ? 'API connected' : 'API unavailable';
}

function heartbeatAge(timestamp) {
  if (!timestamp) return { age: 'Never', exact: 'No heartbeat received' };

  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return { age: 'Unknown', exact: 'Invalid timestamp' };

  const elapsedSeconds = Math.floor((Date.now() - date.getTime()) / 1000);
  const exact = date.toLocaleString();
  if (elapsedSeconds < 0) return { age: 'In the future', exact };
  if (elapsedSeconds < 5) return { age: 'Just now', exact };
  if (elapsedSeconds < 60) return { age: `${elapsedSeconds} seconds ago`, exact };

  const minutes = Math.floor(elapsedSeconds / 60);
  if (minutes < 60) return { age: `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} ago`, exact };

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return { age: `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`, exact };

  const days = Math.floor(hours / 24);
  return { age: `${days} ${days === 1 ? 'day' : 'days'} ago`, exact };
}

function makeCell(className, text) {
  const cell = document.createElement('td');
  if (className) cell.className = className;
  cell.textContent = text;
  return cell;
}

function renderDevices() {
  const statusFilter = elements.filter.value;
  const searchText = elements.search.value.trim().toLocaleLowerCase();
  const visibleDevices = devices.filter((device) => {
    const matchesStatus = statusFilter === 'ALL' || device.status === statusFilter;
    const matchesSearch = !searchText
      || device.id.toLocaleLowerCase().includes(searchText)
      || device.name.toLocaleLowerCase().includes(searchText);
    return matchesStatus && matchesSearch;
  });

  elements.rows.replaceChildren();
  if (visibleDevices.length === 0) {
    elements.tableMessage.textContent = devices.length === 0
      ? 'No devices registered yet. Register a device below or start the simulator.'
      : 'No devices match these filters.';
    elements.tableMessage.hidden = false;
    return;
  }

  elements.tableMessage.hidden = true;
  for (const device of visibleDevices) {
    const row = document.createElement('tr');
    const deviceCell = document.createElement('td');
    const name = document.createElement('div');
    name.className = 'device-name';
    name.textContent = device.name;
    const id = document.createElement('div');
    id.className = 'device-id';
    id.textContent = device.id;
    deviceCell.append(name, id);
    row.append(deviceCell);

    const statusCell = document.createElement('td');
    const badge = document.createElement('span');
    const online = device.status === 'ONLINE';
    badge.className = `status-badge ${online ? 'status-online' : 'status-offline'}`;
    badge.textContent = device.status;
    statusCell.append(badge);
    row.append(statusCell);

    row.append(makeCell('reported-status', device.lastReportedStatus || '—'));

    const heartbeat = heartbeatAge(device.lastHeartbeat);
    const heartbeatCell = document.createElement('td');
    const age = document.createElement('span');
    age.className = 'heartbeat-age';
    age.textContent = heartbeat.age;
    const exact = document.createElement('span');
    exact.className = 'heartbeat-exact';
    exact.textContent = heartbeat.exact;
    heartbeatCell.append(age, exact);
    row.append(heartbeatCell);
    elements.rows.append(row);
  }
}

async function loadFleet() {
  if (isLoading) return;
  isLoading = true;
  elements.refreshButton.disabled = true;
  try {
    const [summary, currentDevices] = await Promise.all([
      fetch('/summary').then(readResponse),
      fetch('/devices').then(readResponse),
    ]);
    devices = currentDevices;
    elements.total.textContent = summary.total;
    elements.online.textContent = summary.online;
    elements.offline.textContent = summary.offline;
    renderDevices();
    elements.lastUpdated.textContent = `Updated ${new Date().toLocaleTimeString()}`;
    setConnection(true);
    clearAlert();
  } catch (error) {
    setConnection(false);
    showAlert(`Could not load fleet data: ${error.message}`);
    if (devices.length === 0) {
      elements.tableMessage.textContent = 'Fleet data is unavailable. Check that the API is running.';
      elements.tableMessage.hidden = false;
    }
  } finally {
    isLoading = false;
    elements.refreshButton.disabled = false;
  }
}

elements.filter.addEventListener('change', renderDevices);
elements.search.addEventListener('input', renderDevices);
elements.refreshButton.addEventListener('click', loadFleet);

elements.registerForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  elements.formMessage.textContent = '';
  elements.formMessage.classList.remove('is-error');

  const formData = new FormData(elements.registerForm);
  const id = formData.get('id').trim();
  const name = formData.get('name').trim();
  if (!id || !name) {
    elements.formMessage.textContent = 'Device ID and name cannot be blank.';
    elements.formMessage.classList.add('is-error');
    return;
  }

  elements.registerButton.disabled = true;
  try {
    await readResponse(await fetch('/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, name }),
    }));
    elements.registerForm.reset();
    elements.formMessage.textContent = `Registered ${name}. It will appear offline until its first heartbeat.`;
    await loadFleet();
  } catch (error) {
    elements.formMessage.textContent = error.message;
    elements.formMessage.classList.add('is-error');
  } finally {
    elements.registerButton.disabled = false;
  }
});

loadFleet();
window.setInterval(loadFleet, refreshIntervalMs);
