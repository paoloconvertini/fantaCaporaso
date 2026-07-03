function hostnameFromRequest(req) {
  const hostHeader = req.headers.host;
  if (!hostHeader) {
    throw new Error('Missing Host header; cannot derive backend proxy target');
  }

  if (hostHeader.startsWith('[')) {
    return hostHeader.slice(0, hostHeader.indexOf(']') + 1);
  }

  return hostHeader.split(':')[0];
}

function backendTarget(req) {
  return `http://${hostnameFromRequest(req)}:8080`;
}

module.exports = {
  '/api': {
    router: backendTarget,
    secure: false,
    changeOrigin: true,
    logLevel: 'debug'
  },
  '/ws': {
    router: backendTarget,
    secure: false,
    ws: true
  }
};
