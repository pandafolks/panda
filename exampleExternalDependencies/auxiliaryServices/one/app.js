const express = require('express')
const fs = require('fs');
const cors = require('cors')
const morgan = require('morgan')

const rawdata1 = fs.readFileSync('example1.json');
const rawdata2 = fs.readFileSync('example2.json');
const jsonExample1 = JSON.parse(rawdata1);
const jsonExample2 = JSON.parse(rawdata2);

const app = express();
app.use(cors())
app.use(morgan('combined'))

const port = 3000;

app.post('/api/v1/cars',(request, response) => {
    console.log(request.body);
    response.end("yes")
});

app.get('/api/v1/cars', (req, res) => {
    res.send(jsonExample1);
})

app.get('/api/v1/cars/rent/', (req, res) => {
    res.send(jsonExample2);
})

app.get('/api/v1/supercars/fixed', (req, res) => {
    res.send(jsonExample2);
})

app.get('/api/v1/supercars/blabla', (req, res) => {
    res.send(jsonExample1);
})

app.get('/api/v1/supercars/blabla/:bla_id', (req, res) => {
    jsonExample1[0]["email"] = req.params.bla_id;
    res.send(jsonExample1);
})

app.get('/api/v1/hb', (req, res) => {
    res.sendStatus(200);
})

app.listen(port, () => {
    console.log(`Example app listening on port ${port}`);
})
