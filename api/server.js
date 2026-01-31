const express = require('express');
const cors = require('cors');
const os = require('os');
const app = express();
const PORT = 3000;

// Enable CORS for all routes
app.use(cors());

// Middleware to parse JSON payloads
app.use(express.json());

// POST endpoint that logs payload to console
app.post('/', (req, res) => {
  const timestamp = new Date().toLocaleTimeString();
  console.log(`[${timestamp}] POST request received:`);
  console.log(req.body);
  res.json({ message: 'Payload received and logged' });
});

// Get local IP address
function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      // Skip internal and non-IPv4 addresses
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

// Start the server
const localIP = getLocalIP();
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server is running on http://${localIP}:${PORT}`);
  console.log(`Also accessible on http://0.0.0.0:${PORT}`);
});
