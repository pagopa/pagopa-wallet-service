module.exports = function (req, res, next) {
  const db = req.app.db
  if (req.method === 'POST') {
    // Converts POST to GET
    req.method = 'GET'
  }
  const requestPath = req.path.toString();
  const requestBody = req.body;
  console.log(`Received request to path : ${requestPath} with body ${JSON.stringify(requestBody)}`)
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
  } else {
    // Continue to default JSON Server router
    next();
  }
}