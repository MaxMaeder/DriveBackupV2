var express = require('express');
var compression = require('compression');

var app = express();

app.use(compression());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(__dirname + '/public'));

app.get('/', function (req, res) {
  res.sendFile(__dirname + '/views/index.html');
});

app.get('/privacy-policy', function(req, res) {
  res.sendFile(__dirname + '/views/privacy-policy.html');
});

app.get('/about', function(req, res) {
  res.sendFile(__dirname + '/views/about.html');
});

app.get('/callback', function(req, res) {
  res.sendFile(__dirname + '/views/success.html');
});

app.get('/drive-selection', function(req, res) {
  res.sendFile(__dirname + '/views/selector.html');
});

app.listen(8080, () => {
   console.log("App listening");
})