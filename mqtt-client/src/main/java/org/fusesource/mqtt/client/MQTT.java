/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.mqtt.client;

import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.transport.*;
import org.fusesource.mqtt.codec.CONNECT;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.fusesource.hawtbuf.Buffer.utf8;
import static org.fusesource.hawtdispatch.Dispatch.createQueue;


/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class MQTT {

    private static final long KEEP_ALIVE = Long.parseLong(System.getProperty("mqtt.thread.keep_alive", ""+1000));
    private static final long STACK_SIZE = Long.parseLong(System.getProperty("mqtt.thread.stack_size", ""+1024*512));
    private static ThreadPoolExecutor blockingThreadPool;


    public synchronized static ThreadPoolExecutor getBlockingThreadPool() {
        if( blockingThreadPool == null ) {
            blockingThreadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, KEEP_ALIVE, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        Thread rc = new Thread(null, r, "MQTT Task", STACK_SIZE);
                        rc.setDaemon(true);
                        return rc;
                    }
                }) {

                    @Override
                    public void shutdown() {
                        // we don't ever shutdown since we are shared..
                    }

                    @Override
                    public List<Runnable> shutdownNow() {
                        // we don't ever shutdown since we are shared..
                        return Collections.emptyList();
                    }
                };
        }
        return blockingThreadPool;
    }
    public synchronized static void setBlockingThreadPool(ThreadPoolExecutor pool) {
        blockingThreadPool = pool;
    }
    
    private static final URI DEFAULT_HOST;
    static {
        try {
            DEFAULT_HOST = new URI("tcp://127.0.0.1:1883");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    URI host = DEFAULT_HOST; 
    URI localURI;
    SSLContext sslContext;
    DispatchQueue dispatchQueue;
    Executor blockingExecutor;
    int maxReadRate;
    int maxWriteRate;
    int trafficClass = TcpTransport.IPTOS_THROUGHPUT;
    int receiveBufferSize = 1024*64;
    int sendBufferSize = 1024*64;
    boolean useLocalHost = true;
    CONNECT connect = new CONNECT();

    long reconnectDelay = 10;
    long reconnectDelayMax = 30*1000;
    double reconnectBackOffMultiplier = 2.0f;
    long reconnectAttemptsMax = -1;
    long connectAttemptsMax = -1;

    public MQTT() {
    }
    public MQTT(MQTT other) {
        this.host = other.host;
        this.localURI = other.localURI;
        this.sslContext = other.sslContext;
        this.dispatchQueue = other.dispatchQueue;
        this.blockingExecutor = other.blockingExecutor;
        this.maxReadRate = other.maxReadRate;
        this.maxWriteRate = other.maxWriteRate;
        this.trafficClass = other.trafficClass;
        this.receiveBufferSize = other.receiveBufferSize;
        this.sendBufferSize = other.sendBufferSize;
        this.useLocalHost = other.useLocalHost;
        this.connect = new CONNECT(other.connect);
    }

    public CallbackConnection callbackConnection() {
        return new CallbackConnection(new MQTT(this));
    }
    public FutureConnection futureConnection() {
        return new FutureConnection(callbackConnection());
    }
    public BlockingConnection blockingConnection() {
        return new BlockingConnection(futureConnection());
    }

    public UTF8Buffer getClientId() {
        return connect.getClientId();
    }

    public short getKeepAlive() {
        return connect.getKeepAlive();
    }

    public UTF8Buffer getPassword() {
        return connect.getPassword();
    }

    public byte getType() {
        return connect.getType();
    }

    public UTF8Buffer getUserName() {
        return connect.getUserName();
    }

    public UTF8Buffer getWillMessage() {
        return connect.getWillMessage();
    }

    public QoS getWillQos() {
        return QoS.values()[connect.getWillQos()];
    }

    public UTF8Buffer getWillTopic() {
        return connect.getWillTopic();
    }

    public boolean isCleanSession() {
        return connect.isCleanSession();
    }

    public boolean isWillRetain() {
        return connect.isWillRetain();
    }

    public void setCleanSession(boolean cleanSession) {
        connect.setCleanSession(cleanSession);
    }

    public void setClientId(String clientId) {
        this.setClientId(utf8(clientId));
    }
    public void setClientId(UTF8Buffer clientId) {
        connect.setClientId(clientId);
    }

    public void setKeepAlive(short keepAlive) {
        connect.setKeepAlive(keepAlive);
    }

    public void setPassword(String password) {
        this.setPassword(utf8(password));
    }
    public void setPassword(UTF8Buffer password) {
        connect.setPassword(password);
    }

    public void setUserName(String password) {
        this.setUserName(utf8(password));
    }
    public void setUserName(UTF8Buffer userName) {
        connect.setUserName(userName);
    }

    public void setWillMessage(String willMessage) {
        connect.setWillMessage(utf8(willMessage));
    }
    public void setWillMessage(UTF8Buffer willMessage) {
        connect.setWillMessage(willMessage);
    }

    public void setWillQos(QoS willQos) {
        connect.setWillQos((byte) willQos.ordinal());
    }

    public void setWillRetain(boolean willRetain) {
        connect.setWillRetain(willRetain);
    }

    public void setWillTopic(String password) {
        this.setWillTopic(utf8(password));
    }
    public void setWillTopic(UTF8Buffer willTopic) {
        connect.setWillTopic(willTopic);
    }

    public Executor getBlockingExecutor() {
        return blockingExecutor;
    }

    public void setBlockingExecutor(Executor blockingExecutor) {
        this.blockingExecutor = blockingExecutor;
    }

    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue dispatchQueue) {
        this.dispatchQueue = dispatchQueue;
    }

    public URI getLocalURI() {
        return localURI;
    }

    public void setLocalURI(URI localURI) {
        this.localURI = localURI;
    }

    public int getMaxReadRate() {
        return maxReadRate;
    }

    public void setMaxReadRate(int maxReadRate) {
        this.maxReadRate = maxReadRate;
    }

    public int getMaxWriteRate() {
        return maxWriteRate;
    }

    public void setMaxWriteRate(int maxWriteRate) {
        this.maxWriteRate = maxWriteRate;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public URI getHost() {
        return host;
    }
    public void setHost(String host, int port) throws URISyntaxException {
        this.setHost(new URI("tcp://"+host+":"+port));
    }
    public void setHost(String host) throws URISyntaxException {
        this.setHost(new URI(host));
    }
    public void setHost(URI host) {
        this.host = host;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public void setTrafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
    }

    public boolean isUseLocalHost() {
        return useLocalHost;
    }

    public void setUseLocalHost(boolean useLocalHost) {
        this.useLocalHost = useLocalHost;
    }

    public long getConnectAttemptsMax() {
        return connectAttemptsMax;
    }

    public void setConnectAttemptsMax(long connectAttemptsMax) {
        this.connectAttemptsMax = connectAttemptsMax;
    }

    public long getReconnectAttemptsMax() {
        return reconnectAttemptsMax;
    }

    public void setReconnectAttemptsMax(long reconnectAttemptsMax) {
        this.reconnectAttemptsMax = reconnectAttemptsMax;
    }

    public double getReconnectBackOffMultiplier() {
        return reconnectBackOffMultiplier;
    }

    public void setReconnectBackOffMultiplier(double reconnectBackOffMultiplier) {
        this.reconnectBackOffMultiplier = reconnectBackOffMultiplier;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public long getReconnectDelayMax() {
        return reconnectDelayMax;
    }

    public void setReconnectDelayMax(long reconnectDelayMax) {
        this.reconnectDelayMax = reconnectDelayMax;
    }

}
