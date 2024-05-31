module.exports = function (req, res, next) {
  const db = req.app.db
  if (req.method === 'POST') {
    // Converts POST to GET
    req.method = 'GET'
  }
  const requestPath = req.path.toString();
  const requestBody = req.body;
  console.log(`${new Date().toISOString()} - Received request: ${req.method} ${req.path}
     Headers:
     ${JSON.stringify(req.headers)}
     Path: ${requestPath}
     Body:
     ${JSON.stringify(req.body)}
     `);

  if (requestPath.includes("fees")) {
    res.json(db.get("feesV2"));
  } else {
    next();
  }
}