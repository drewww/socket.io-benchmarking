// io.js - A thin io layer for socket.io.
//
// Holds socket.io connections and relays messages to a logic core over
// AMQP.

var app = require('express').createServer(),
    io = require('socket.io').listen(app),
    winston = require('winston'),
    program = require('commander'),
    amqp = require('amqp');


program.version('0.1')
    .option('-v, --verbose', 'Turns on verbose logging.')
    .option('-p, --port', 'Set the websocket port to bind to (default 8080)')
    .parse(process.argv);

var setLevel = "info";
if(program.verbose) {
    setLevel = "debug";
}

var logger = new (winston.Logger)({
    transports: [
      new (winston.transports.Console)({timestamp:true, level:setLevel, colorize:true}),
    ]
  });
logger.setLevels(winston.config.syslog.levels);


var queueHost = "localhost";
if(program.args.length>=0) {
    queueHost = program.args[0];
} else if (program.args.length==0) {
    logger.warn("Default to localhost for queue host.");
}

var port = 8080;
if(program.port) {
    port = program.port
    logger.info("Setting port to " + program.port);
}

app.listen(port);

var connection = amqp.createConnection({host: queueHost});

// When we get a connection to the queue, do basic setup work.
var exchange;
var queue;
connection.on('ready', function() {
    var e = connection.exchange('socket-events',{"type":"topic"}, function(newExchange) {
        logger.info("Exchange " + newExchange.name + " is open for business.");
        
        exchange = newExchange;
        
        var q = connection.queue("", {"exclusive":true}, function(newQueue) {
            logger.info("Queue " + newQueue.name + " is open for business.");

            queue = newQueue;
            
            // Do binding setup.
            // The message hierarchy is going to be:
            // broadcast
            // room.room-name
            // user.user-id
            queue.bind(exchange.name, "broadcast");

            var response = queue.subscribe(dequeue);
            logger.debug("subscribe response: " + response);
        });
    });
    
    logger.debug("e: " + e);
});


function dequeue(message, headers, deliveryInfo) {
    logger.info("Got a message with key: " + deliveryInfo.routingKey + " and message: " + message);
}