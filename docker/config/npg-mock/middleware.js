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
     Body:
     ${JSON.stringify(req.body)}
     `);
  if (requestPath.includes("build")) {
    //handle build data request changing response based on input paymentService
    const paymentService = requestBody.paymentSession.paymentService;
    console.log(`Received build request for payment session: ${paymentService}`);
    if (paymentService == "CARDS") {
      res.json(db.get("buildCards"))
    }
    else {
      res.json(db.get("buildApm"))
    }
  } else if(requestPath.includes("deactivation")) { // POST /psp/api/v1/contracts/{contractId}/deactivation
    const match = requestPath.match(/\/contracts\/([^\/]+)\/deactivation/);
    const contractId = match ? match[1] : null;

    const correlationId = req.headers['correlation-id'] || req.headers['x-correlation-id'];

    if (contractId === "BAD_REQUEST") {
      return res.status(400).json({
        errors: [
          {
            code: "XY0000",
            description: "Bad request - generic error"
          }
        ]
      });
    }

    if (contractId === "BAD_REQUEST_PS0017") {
      return res.status(400).json({
        errors: [
          {
            code: "PS0017",
            description: `Merchant contract id not found - ${correlationId}`
          }
        ]
      });
    }

    return res.status(200).send();
  } else {
    // Continue to default JSON Server router
    next();
  }
}