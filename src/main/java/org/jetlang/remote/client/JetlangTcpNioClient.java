package org.jetlang.remote.client;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.JetlangMessageHandler;
import org.jetlang.remote.acceptor.JetlangNioSession;
import org.jetlang.remote.acceptor.NioJetlangProtocolReader;
import org.jetlang.remote.acceptor.NioJetlangRemotingClientFactory;
import org.jetlang.remote.acceptor.NioJetlangSendFiber;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.TcpClientNioConfig;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.remote.core.TopicReader;

import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

/**
 */
public class JetlangTcpNioClient<R, W>  {

    private final TcpClientNioConfig tcpConfig;
    private final NioJetlangSendFiber<W> sendFiber;
    private final JetlangClientConfig config;
    private final Serializer<R, W> ser;
    private final ErrorHandler errorHandler;
    private final TcpClientNioFiber readFiber;

    public JetlangTcpNioClient(SocketConnector socketConnector,
                               NioJetlangSendFiber<W> sendFiber,
                               JetlangClientConfig config,
                               Serializer<R, W> ser,
                               ErrorHandler errorHandler,
                               JetlangNioSession.ErrorHandler<R> nioErrorHandler,
                               TcpClientNioFiber readFiber, TopicReader topicReader) {
        JetlangMessageHandler<R> msgHandler = new JetlangMessageHandler<R>() {
            private final Object id = new Object();
            @Override
            public Object getSessionId() {
                return id;
            }

            @Override
            public void onReadTimeout(ReadTimeoutEvent readTimeoutEvent) {

            }

            @Override
            public void onMessage(String dataTopicVal, R readObject) {

            }

            @Override
            public void onHb() {

            }

            @Override
            public void onLogout() {

            }

            @Override
            public void onSubscriptionRequest(String val) {
                throw new RuntimeException("Subscription not supported: " + val);
            }

            @Override
            public void onRequest(int reqId, String dataTopicVal, R readObject) {
                throw new RuntimeException("Request/Reply not supported: " + dataTopicVal + " " + readObject);
            }

            @Override
            public void onUnsubscribeRequest(String val) {
                throw new RuntimeException("Unsubscribe not supported: " + val);
            }

            @Override
            public void onUnknownMessage(int read) {
                throw new RuntimeException("Unknown message: " + read);
            }

            @Override
            public void onRequestReply(int reqId, String dataTopicVal, R readObject) {
                throw new RuntimeException("Request/Reply not supported: " + dataTopicVal + " " + readObject);
            }

            @Override
            public void onHandlerException(Exception failed) {
                errorHandler.onException(failed);
            }
        };
        this.tcpConfig = new TcpClientNioConfig.Default(errorHandler, socketConnector::getInetSocketAddress,
                new JetlangClientFactory<R, W>(ser, sendFiber, nioErrorHandler, topicReader, msgHandler), config.getInitialConnectDelayInMs(),
                config.getReconnectDelayInMs(), TimeUnit.MILLISECONDS);
        this.sendFiber = sendFiber;
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
        this.readFiber = readFiber;
    }

    public void start(){
        //readFiber.connect()
    }

    private static class JetlangClientFactory<R, W> implements TcpClientNioConfig.ClientFactory {

        private final Serializer<R, W> ser;
        private final NioJetlangSendFiber<W> sendFiber;
        private final JetlangNioSession.ErrorHandler<R> errorHandler;
        private final TopicReader topicReader;
        private final JetlangMessageHandler<R> msgHandler;

        public JetlangClientFactory(Serializer<R, W> ser, NioJetlangSendFiber<W> sendFiber, JetlangNioSession.ErrorHandler<R> errorHandler,
                                    TopicReader topicReader, JetlangMessageHandler<R> msgHandler){
            this.ser = ser;
            this.sendFiber = sendFiber;
            this.errorHandler = errorHandler;
            this.topicReader = topicReader;
            this.msgHandler = msgHandler;
        }

        @Override
        public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer) {
            //JetlangNioSession<R, W> session = new JetlangNioSession<R, W>(nioFiber, chan, sendFiber, new NioJetlangRemotingClientFactory.Id(chan), errorHandler);
            NioJetlangProtocolReader<R> reader = new NioJetlangProtocolReader<R>(chan, msgHandler, ser.getReader(), topicReader);
            NioJetlangRemotingClientFactory.Id chanId = new NioJetlangRemotingClientFactory.Id(chan);
            NioJetlangSendFiber.ChannelState channel = new NioJetlangSendFiber.ChannelState(chan, chanId, nioFiber);
            this.sendFiber.onNewSession(channel);

            return new TcpClientNioFiber.ConnectedClient() {
                @Override
                public boolean read(SocketChannel chan) {
                    return reader.read();
                }

                @Override
                public void onDisconnect() {
                    sendFiber.handleClose(channel);
                }
            };
        }
    }
}
