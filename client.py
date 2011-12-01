#!/usr/bin/env python
# encoding: utf-8
"""
client.py

Based heavily on:
 - https://github.com/mtah/python-websocket/blob/master/examples/echo.py
 - http://stackoverflow.com/a/7586302/316044

Created by Drew Harry on 2011-11-28.
Copyright (c) 2011 MIT Media Lab. All rights reserved.
"""

import websocket, httplib, sys, asyncore, json, threading, traceback, time
import argparse, random

'''
    connect to the socketio server

    1. perform the HTTP handshake
    2. open a websocket connection '''
    
    
class Client(object):
    
    DISCONNECTED = 0
    CONNECTED = 1
    IDENTIFIED = 2
    JOINED_ROOM = 3
    
    messageQueue = []
    messageFlushingEngaged = False
    
    def __init__(self, server, port):
        if(Client.messageFlushingEngaged is False):
            Client.messageFlushingEngaged = True
            threading.Timer(1.0, Client.flushEventQueue).start()
    
        conn  = httplib.HTTPConnection(server + ":" + str(port))
        conn.request('POST','/socket.io/1/')
        resp  = conn.getresponse() 
        hskey = resp.read().split(':')[0]

        self.ws = websocket.WebSocket(
                        'ws://'+server+':'+str(port)+'/socket.io/1/websocket/'+hskey,
                        onopen   = self._onopen,
                        onmessage = self._onmessage,
                        onclose = self._onclose,
                        onerror = self._onerror)
        self.state = Client.DISCONNECTED
        
            

    def _onopen(self):
        self.state = Client.CONNECTED
        
        Client.addMessage("open")
    
    def _onmessage(self, msg):
        
        # Client.addMessage("message")
        
        # print(str(id(self)) + ": " + msg)
        if(msg[0]=="5"):
            payload = json.loads(msg.lstrip('5:'))
            
            Client.addMessage("m_" + payload["name"])
            

    def _onclose(self):
        Client.addMessage("close")
        
    
    def _onerror(self, t, e, trace):
        Client.addMessage("error")
        
        traceback.print_tb(trace)
        print(str(id(self)) + " ERR: " + str(e) + "; " + str(t))
    
    def close(self):
        self.ws.close()
        self.state = Client.DISCONNECTED
        
    def heartbeat(self):
        if(self.state!=Client.DISCONNECTED):            
            Client.addMessage("heartbeat")
            
            threading.Timer(15.0, self.heartbeat).start()
            self.ws.send('2:::')
    
    def sendChat(self, msg):
        self.ws.send('5:::{"name":"chat", "args":[{"text":"'+msg+'"}]}')
    
    @staticmethod
    def addMessage(event):
        Client.messageQueue.append(event)
    
    @staticmethod
    def flushEventQueue():
        if(not Client.messageFlushingEngaged):
            return
            
        # if(Client.lastMessageFlush is None or ((time.time() - Client.lastMessageFlush)>1)):
        threading.Timer(1.0, Client.flushEventQueue).start()
        Client.lastMessageFlush = time.time()
        
        # count up all the different types and do a one line summary
        d = {"open":0, "close":0, "heartbeat":0, "error":0, "m_identify":0, "m_chat":0, "m_pulse":0, "m_rooms":0, "m_bots":0}
        for i in set(Client.messageQueue):
            d[i] = Client.messageQueue.count(i)
        
        Client.messageQueue = []
        
        outputString = ""
        for i in d:
            outputString = outputString + i + ": {:<5} ".format(str(d[i]))
            # outputString = outputString + i + ": " + str(d[i]) + " "
            
        print(outputString)


# this is going to be called once a second, so figure out how many messages
# we're supposed to send to keep up.
def processChat():
    global shutdown
    if(shutdown):
        return
    
    threading.Timer(1.0, processChat).start()
    messagesPerSecond = chat/60
    
    for i in range(0, int(messagesPerSecond)):
        clients[i].sendChat("this is a chat message!")
        time.sleep(1.0/messagesPerSecond)
    

clients = []
chat = 0.0
shutdown = False

if __name__ == '__main__':
    
    parser = argparse.ArgumentParser(description="Load tester for socket.io applications (customized for ROAR)")
    parser.add_argument('-p', '--port', action='store', default=8888)
    parser.add_argument('-c', '--concurrency', type=int, default=1)
    parser.add_argument('-C', '--chat',metavar="MSGS_PER_MIN_CLIENT", default=0)
    parser.add_argument('server')
    
    args = parser.parse_args(sys.argv[1:])
    print(str(args))
    
    server = args.server
    port = int(args.port)
    num_clients = args.concurrency
    
    global chat
    chat = int(args.chat)
    
    print("connecting to: %s:%d x%d" %(server, port, num_clients))
    
    for index in range(0, num_clients):
        client = Client(server, port)
        clients.append(client)
    
    if(chat>0):
        threading.Timer(5.0, processChat).start()
    
    print("All clients created!")
    
    try:
        asyncore.loop()
    except KeyboardInterrupt:
        print("Closing all connections...")
        Client.messageFlushingEngaged = False
        global shutdown
        shutdown = True
        for client in clients:
            client.close()
    
    


