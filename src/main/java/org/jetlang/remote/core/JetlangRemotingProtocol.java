package org.jetlang.remote.core;

import org.jetlang.channels.Publisher;
import org.jetlang.remote.client.RemoteSubscriptions;

import java.io.IOException;
import java.nio.ByteBuffer;

public class JetlangRemotingProtocol<T> {

    public ByteBuffer buffer;
    public byte[] bufferArray = new byte[1024 * 8];
    private final Handler<T> session;
    private final TopicReader topicReader;
    private final DataRequest dataRequest = new DataRequest();
    private final DataRequestReply dataRequestReply = new DataRequestReply();
    private final DataReader dataReader = new DataReader() {
        @Override
        protected State onObject(String dataTopicVal, T readObject) {
            try {
                session.onMessage(dataTopicVal, readObject);
            } catch (Exception failed) {
                session.onHandlerException(failed);
            }
            return root;
        }
    };
    public final State root = new State() {
        @Override
        public int getRequiredBytes() {
            return 1;
        }

        @Override
        public State run() {
            int read = buffer.get();
            switch (read) {
                case MsgTypes.Heartbeat:
                    execEvent(session::onHb);
                    return this;
                case MsgTypes.Subscription:
                    return subRequest.first;
                case MsgTypes.Unsubscribe:
                    return unsubRequest.first;
                case MsgTypes.Disconnect:
                    execEvent(session::onLogout);
                    return this;
                case MsgTypes.Data:
                    return dataReader.first.first;
                case MsgTypes.DataRequest:
                    return dataRequest.reqIdSt;
                case MsgTypes.DataReply:
                    return dataRequestReply.reqIdSt;
                default:
                    session.onUnknownMessage(read);
                    return this;
            }
        }
    };

    private void execEvent(Runnable event) {
        try {
            event.run();
        } catch (Exception failed) {
            session.onHandlerException(failed);
        }
    }

    private final StringState subRequest = new StringState() {
        @Override
        protected State onString(String val) {
            try {
                session.onSubscriptionRequest(val);
            } catch (Exception failed) {
                session.onHandlerException(failed);
            }
            return root;
        }
    };
    private final StringState unsubRequest = new StringState() {
        @Override
        protected State onString(String val) {
            try {
                session.onUnsubscribeRequest(val);
            } catch (Exception failed) {
                session.onHandlerException(failed);
            }
            return root;
        }
    };
    private final ObjectByteReader<T> reader;

    public void resizeBuffer(int requiredBytes) {
        bufferArray = new byte[requiredBytes];
        ByteBuffer b = ByteBuffer.wrap(bufferArray);
        buffer.flip();
        b.put(buffer);
        buffer = b;
    }

    public interface Handler<T> {

        void onMessage(String dataTopicVal, T readObject);

        void onSubscriptionRequest(String val);

        void onRequest(int reqId, String dataTopicVal, T readObject);

        void onUnsubscribeRequest(String val);

        void onHb();

        void onLogout();

        void onUnknownMessage(int read);

        void onRequestReply(int reqId, String dataTopicVal, T readObject);

        void onHandlerException(Exception failed);
    }

    public interface MessageDispatcher {

        <R> void dispatch(String dataTopicVal, R readObject);
    }

    public static abstract class ClientHandler<R> implements Handler<R> {

        private final ErrorHandler errorHandler;
        private final Publisher<HeartbeatEvent> hb;
        private final MessageDispatcher subscriptions;

        public ClientHandler(ErrorHandler errorHandler, Publisher<HeartbeatEvent> hb, MessageDispatcher subscriptions) {
            this.errorHandler = errorHandler;
            this.hb = hb;
            this.subscriptions = subscriptions;
        }

        @Override
        public void onMessage(String dataTopicVal, R readObject) {
            subscriptions.dispatch(dataTopicVal, readObject);
        }

        @Override
        public void onSubscriptionRequest(String val) {
            errorHandler.onException(new IOException("SubscriptionNotSupported: " + val));
        }

        @Override
        public void onRequest(int reqId, String dataTopicVal, Object readObject) {
            errorHandler.onException(new IOException("RequestNotSupported: " + dataTopicVal + " val: " + readObject));
        }

        @Override
        public void onUnsubscribeRequest(String val) {
            errorHandler.onException(new IOException("UnsubscribeNotSupported: " + val));
        }

        @Override
        public void onHandlerException(Exception failed) {
            errorHandler.onException(failed);
        }

        @Override
        public void onHb() {
            hb.publish(new HeartbeatEvent());
        }

        @Override
        public void onUnknownMessage(int read) {
            errorHandler.onException(new IOException(read + " not supported"));
        }
    }

    public JetlangRemotingProtocol(Handler<T> session, ObjectByteReader<T> reader, TopicReader charset) {
        this.session = session;
        this.topicReader = charset;
        this.buffer = ByteBuffer.wrap(this.bufferArray);
        this.reader = reader;
    }

    public interface State {
        int getRequiredBytes();

        State run() throws IOException;
    }

    private abstract class StringState {
        private int stringSize;
        State getSubRequestString = new State() {
            @Override
            public int getRequiredBytes() {
                return stringSize;
            }

            @Override
            public State run() throws IOException {
                String val = topicReader.read(bufferArray, buffer.position(), stringSize);
                buffer.position(buffer.position() + stringSize);
                return onString(val);
            }
        };
        State first = new State() {
            @Override
            public int getRequiredBytes() {
                return 1;
            }

            @Override
            public State run() {
                stringSize = buffer.get();
                return getSubRequestString;
            }
        };

        protected abstract State onString(String val) throws IOException;
    }

    private abstract class DataReader {
        private int dataSizeVal;
        private String dataTopicVal;
        State dataSizeRead = new State() {
            @Override
            public int getRequiredBytes() {
                return dataSizeVal;
            }

            @Override
            public State run() throws IOException {
                final T readObject = reader.readObject(dataTopicVal, bufferArray, buffer.position(), dataSizeVal);
                buffer.position(buffer.position() + dataSizeVal);
                return onObject(dataTopicVal, readObject);
            }
        };
        State dataSize = new State() {
            @Override
            public int getRequiredBytes() {
                return 4;
            }

            @Override
            public State run() {
                dataSizeVal = buffer.getInt();
                return dataSizeRead;
            }
        };
        StringState first = new StringState() {
            @Override
            protected State onString(String val) {
                dataTopicVal = val;
                return dataSize;
            }
        };

        protected abstract State onObject(String dataTopicVal, T readObject) throws IOException;
    }

    private class DataRequest extends DataRequestBase {
        @Override
        protected void handleRequest(int reqId, String dataTopicVal, T readObject) {
            session.onRequest(reqId, dataTopicVal, readObject);
        }
    }

    private class DataRequestReply extends DataRequestBase {

        @Override
        protected void handleRequest(int reqId, String dataTopicVal, T readObject) {
            session.onRequestReply(reqId, dataTopicVal, readObject);
        }
    }

    private abstract class DataRequestBase {
        int reqId;

        DataReader data = new DataReader() {
            @Override
            protected State onObject(String dataTopicVal, T readObject) {
                try {
                    handleRequest(reqId, dataTopicVal, readObject);
                } catch (Exception failed) {
                    session.onHandlerException(failed);
                }
                return root;
            }
        };

        protected abstract void handleRequest(int reqId, String dataTopicVal, T readObject);

        State reqIdSt = new State() {
            @Override
            public int getRequiredBytes() {
                return 4;
            }

            @Override
            public State run() {
                reqId = buffer.getInt();
                return data.first.first;
            }
        };
    }

}
