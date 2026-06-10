const statusEl = document.getElementById('status');
const termEl = document.getElementById('terminal');

const term = new Terminal({
  fontSize: 13,
  fontFamily: 'Menlo, Monaco, "Courier New", monospace',
  cursorBlink: true,
  scrollback: 8000,
});
const fitAddon = new FitAddon.FitAddon();
term.loadAddon(fitAddon);
term.open(termEl);

function resizeTerminal() {
  fitAddon.fit();
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'resize', cols: term.cols, rows: term.rows }));
  }
}

let socket = null;

function connect() {
  const wsUrl = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/term`;
  socket = new WebSocket(wsUrl);

  socket.addEventListener('open', () => {
    statusEl.textContent = 'Connected';
    resizeTerminal();
  });

  socket.addEventListener('message', (event) => {
    term.write(event.data);
  });

  socket.addEventListener('close', () => {
    statusEl.textContent = 'Disconnected';
  });

  socket.addEventListener('error', () => {
    statusEl.textContent = 'Error';
  });
}

term.onData((data) => {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'input', data }));
  }
});

window.addEventListener('resize', () => resizeTerminal());

connect();
resizeTerminal();
