const express = require('express');
const compression = require('compression');

const app = express();

app.use(compression());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(__dirname + '/public'));
app.use(express.static(__dirname + '/views'));

app.listen(8080, () => {
   console.log("App listening");
})