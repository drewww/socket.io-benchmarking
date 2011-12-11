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

io.set("log level", 0);

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
        });
    });
    
    logger.debug("e: " + e);
});

var clients = [];
io.sockets.on('connection', function(socket) {
    
    // logger.info("Received connection.");
    publishEventFromSocket(socket, "connected", null, true);
    
    // Do some startup stuff. For now, nothing.
    socket.on('message', function(data) {
        // When we get a message, forward it on to the server.
        publishMessageFromSocket(socket, data, false);
    });
    
    socket.on('disconnect', function(data) {
        publishEventFromSocket(socket, "disconnected", null, true);
    });
});

function publishMessageFromSocket(socket, body, protocolMessage) {
    exchange.publish("user." + socket.id, body,
        {"headers":{"protocol-message":protocolMessage}});
}

function publishEventFromSocket(socket, eventName, args, protocolMessage) {
    publishMessageFromSocket(socket, createMessage(eventName, args), protocolMessage);
}

function createMessage(eventName, args) {
    if(args==null) {
        args = [];
    }
    
    return JSON.stringify({"name":eventName, "args":args});
}

function dequeue(message, headers, deliveryInfo) {
    // logger.info("Got a message with key: " + deliveryInfo.routingKey + " and message: " + message);
    
    // This is where routing logic goes. Look at the routingKey and make 
    // choices about which of our connected clients this shoud be routed to.
    if(deliveryInfo.routingKey=="broadcast") {
        // Send to everyone.
        
        message = JSON.parse(message.data.toString());
        
        io.sockets.emit(message.name, {"text":message.args[0].text});        
    } else {
        // Do something else.
    }
}