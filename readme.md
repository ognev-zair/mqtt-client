## Overview

mqtt-client provides an ASL 2.0 licensed API to MQTT.  Applications can use a blocking API style,
a futures based API, or a callback/continuations passing API style.

## Configuring the MQTT Connection

The blocking, future, and callback APIs all share the same connection setup.  You create a new 
instance of the `MQTT` class and configure it with connection and socket related options. At a minimum
the `host` and `clientId` fields must be set before attempting to connect.

    MQTT mqtt = new MQTT();
    mqtt.setHost("localhost", 1883);
    mqtt.setClientId("Hiram");

## Using the Blocking API

The `MQTT.connectBlocking` method establishes a connection and provides you a connection
with an blocking API.

    BlockingConnection connection = mqtt.blockingConnection();
    connection.connect();

Publish messages to a topic using the `publish` method:

    connection.publish("foo", "Hello".toBytes(), QoS.AT_LEAST_ONCE, false);

You can subscribe to multiple topics using the the `subscribe` method:
    
    Topic[] topics = {new Topic("foo", QoS.AT_LEAST_ONCE)};
    byte[] qoses = connection.subscribe(topics);

Then receive and acknowledge consumption of messages using the `receive`, and `ack`
methods:
    
    Message message = connection.receive();
    System.out.println(message.getTopic());
    byte[] payload = message.getPayload();
    // process the message then:
    message.ack();

Finally to disconnect:

    connection.disconnect();

## Using the Future based API

The `MQTT.connectFuture` method establishes a connection and provides you a connection
with an futures style API.  All operations against the connection are non-blocking and
return the result via a Future.

    FutureConnection connection = mqtt.futureConnection();
    Future<Void> f1 = connection.connect();
    f1.await();

    Future<byte[]> f2 = connection.subscribe(new Topic[]{new Topic(utf8("foo"), QoS.AT_LEAST_ONCE)});
    byte[] qoses = f2.await();

    // We can start future receive..
    Future<Message> receive = connection.receive();

    // send the message..
    Future<Void> f3 = connection.publish("foo", "Hello".getBytes(), QoS.AT_LEAST_ONCE, false);

    // Then the receive will get the message.
    Message message = receive.await();
    message.ack();
    
    Future<Void> f4 connection.disconnect();
    f4.await();


## Using the Callback/Continuation Passing based API

The `MQTT.connectCallback` method establishes a connection and provides you a connection with
an callback style API. This is the most complex to use API style, but can provide the best
performance. The future and blocking APIs use the callback api under the covers. All
operations on the connection are non-blocking and results of an operation are passed to
callback interfaces you implement.

Example:

    final CallbackConnection connection = mqtt.callbackConnection();
    connection.listener(new Listener() {
      
        public void onDisconnected() {
        }
        public void onConnected() {
        }

        public void onSuccess(UTF8Buffer topic, Buffer payload, Runnable ack) {
            // You can now process a received message from a topic.
            // Once process execute the ack runnable.
            ack.run();
        }
        public void onFailure(Throwable value) {
            connection.close(null); // a connection failure occured.
        }
    })
    connection.connect(new Callback<Void>() {
        public void onFailure(Throwable value) {
            result.failure(value); // If we could not connect to the server.
        }
  
        // Once we connect..
        public void onSuccess(Void v) {
        
            // Subscribe to a topic
            Topic[] topics = {new Topic("foo", QoS.AT_LEAST_ONCE)};
            connection.subscribe(topics, new Callback<byte[]>() {
                public void onSuccess(byte[] qoses) {
                    // The result of the subcribe request.
                }
                public void onFailure(Throwable value) {
                    connection.close(null); // subscribe failed.
                }
            });

            // Send a message to a topic
            connection.publish("foo", "Hello".getBytes(), QoS.AT_LEAST_ONCE, false, new Callback<Void>() {
                public void onSuccess(Void v) {
                  // the pubish operation completed successfully.
                }
                public void onFailure(Throwable value) {
                    connection.close(null); // publish failed.
                }
            });
            
            // To disconnect..
            connection.disconnect(new Callback<Void>() {
                public void onSuccess(Void v) {
                  // called once the connection is disconnected.
                }
                public void onFailure(Throwable value) {
                  // Disconnects never fail.
                }
            });
        }
    });

Every connection has a [HawtDispatch](http://hawtdispatch.fusesource.org/) dispatch queue
which it uses to process IO events for the socket. The dispatch queue is an Executor that
provides serial execution of IO and processing events and is used to ensure synchronized
access of connection.

The callbacks will be executing the the the dispatch queue associated with the connection so
it safe to use the connection from the callback but you MUST NOT perform any blocking
operations within the callback. If you need to perform some processing which MAY block, you
must send it to another thread pool for processing. Furthermore, if another thread needs to
interact with the connection it can only doit by using a Runnable submitted to the
connection's dispatch queue.

Example of executing a Runnable on the connection's dispatch queue:

    connection.getDispatchQueue().execute(new Runnable(){
        public void run() {
          connection.publish( ..... );
        }
    });
