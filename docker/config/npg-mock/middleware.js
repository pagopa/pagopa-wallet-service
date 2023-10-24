module.exports = function (req, res, next) {
    if (req.method === 'POST') {
      // Converts POST to GET
      req.method = 'GET'
    }
    // Continue to JSON Server router
    next()
  }