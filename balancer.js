// balancer.js - a super simple load balancer for managing multiple instances
//               of io.js.
//
// Communicates with instances of io.js over amqp to figure out their current
// loads. Responds to http requests with the connection information for the
// io.js instance that client should connect to. Always picks the io.js 
// instance with the fewest currently connected users. There is a simple 
// protocol between io.js instances and a balancer.js instance.
//
// io.js messages:
//  - balancer.status: {"host":"host-url", "socket-count":"num-of-sockets"}
//
// balancer.js messages:
//  - io.status: {} - prompts status responses


var app = require('express').createServer(),
    io = require('socket.io').listen(app),
    winston = require('winston'),
    program = require('commander'),
    amqp = require('amqp');

    program.version('0.1')
        .option('-v, --verbose', 'Turns on verbose logging.')
        .option('-p, --port <num>', 'Set the websocket port to bind to (default 8080)')
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

app.get('/', function(req, res) {
    // This is where we decide which server we should route this request to.
    // First approach, we'll just sort the dictionary and figure out which is 
    // the lowest loaded server. Route them to that server and increment
    // their count (speculatively).
    
    if(ioNodes.length==0) {
        res.send("No servers available right now.", 502);
    }
    
    var hostToUse;
    var currentLow = 1000000;
    for(var socketHost in ioNodes) {
        logger.info("socketHost: " + socketHost + " (with "+ioNodes[socketHost]+")");
        if(ioNodes[socketHost] < currentLow) {
            hostToUse = socketHost;
            currentLow = ioNodes[socketHost];
            
            // Up the value to sort of fake a round-robin effect until we
            // hear back from a node telling us what its load actually is.
        }
    }
    
    logger.info("Telling client to use " + hostToUse + " ("+currentLow+" sockets)");
    ioNodes[hostToUse] = ioNodes[hostToUse]+1;    
    res.send(hostToUse);
});

var connection = amqp.createConnection({host: queueHost});

// When we get a connection to the queue, do basic setup work.
var exchange;
var queue;

var ioNodes = {};

connection.on('ready', function() {
    var e = connection.exchange('io-balancer',{"type":"topic"}, function(newExchange) {
        logger.info("Connected to balance exchange: " + newExchange.name);

        exchange = newExchange;

        var q = connection.queue("", {"exclusive":true}, function(newQueue) {
            logger.info("Queue " + newQueue.name + " is open for business.");
        
            queue = newQueue;
        
            queue.bind(exchange.name, "balancer.*");
        
            var response = queue.subscribe(dequeue);
        });
        
        // Blast a hello message so we can find io nodes.
        //exchange.publish("io.status", "this is my empty message");
        
        ping();
    });
});

function dequeue(message, headers, deliveryInfo) {
    if(deliveryInfo.routingKey=="balancer.status") {
        message = JSON.parse(message.data.toString());
        
        // Keep track of this information.
        if(!(message.host in ioNodes)) {
            logger.info("Registered a new host: " + message.host);
        } else {
            logger.info("server: " + message.host + " @" + message["socket-count"]);
        }
        
        ioNodes[message.host] = message["socket-count"];
    }
}

function ping() {
    setTimeout(ping, 5000);
    logger.debug("Asking io nodes for status.");
    exchange.publish("io.status", "empty message", {});
}
