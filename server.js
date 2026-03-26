const express = require('express');
const app = express();
const PORT = 3001;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Enable CORS
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  next();
});

// Mock API endpoints
app.get('/api/status', (req, res) => {
  res.json({
    status: 'online',
    timestamp: new Date().toISOString(),
    version: 'v0.1.0'
  });
});

app.post('/api/uwb/range', (req, res) => {
  const { device_id, distance_m, confidence } = req.body;
  console.log('[UWB] Range request:', { device_id, distance_m, confidence });
  
  res.status(201).json({
    success: true,
    range_id: `rng_${Date.now()}`,
    measured_at: new Date().toISOString(),
    distance_m: distance_m || 1.42,
    confidence: confidence || 0.96
  });
});

app.listen(PORT, () => {
  console.log(`✅ Mock server running at http://localhost:${PORT}`);
  console.log(`➡️  Try: curl -X POST http://localhost:${PORT}/api/uwb/range -H "Content-Type: application/json" -d '{"device_id":"uwb-001","distance_m":1.25}'`);
});