package org.jetlang.remote.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class JetlangRemotingProtocol {

    public ByteBuffer buffer;
    public byte[] bufferArray = new byte[1024 * 8];
    private final Handler session;
    private final Charset charset;
    private final DataRequest dataRequest = new DataRequest();
    private final DataRequestReply dataRequestReply = new DataRequestReply();
    private final DataReader d = new DataReader() {
        @Override
        protected State onObject(String dataTopicVal, Object readObject) {
            try {
                session.onMessage(dataTopicVal, readObject);
            } catch (Exception failed) {
                session.onHandlerException(failed);
            }
            return root;
        }
    };
    public final State root = new State() {
        public int getRequiredBytes() {
            return 1;
        }

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
                    return d.first.first;
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
    private final ObjectByteReader reader;

    public void resizeBuffer(int requiredBytes) {
        bufferArray = new byte[requiredBytes];
        ByteBuffer b = ByteBuffer.wrap(bufferArray);
        buffer.flip();
        b.put(buffer);
        buffer = b;
    }

    public interface Handler {

        void onMessage(String dataTopicVal, Object readObject);

        void onSubscriptionRequest(String val);

        void onRequest(int reqId, String dataTopicVal, Object readObject);

        void onUnsubscribeRequest(String val);

        void onHb();

        void onLogout();

        void onUnknownMessage(int read);

        void onRequestReply(int reqId, String dataTopicVal, Object readObject);

        void onHandlerException(Exception failed);
    }

    public JetlangRemotingProtocol(Handler session, ObjectByteReader reader, Charset charset) {
        this.session = session;
        this.charset = charset;
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
            public int getRequiredBytes() {
                return stringSize;
            }

            public State run() throws IOException {
                String val = new String(bufferArray, buffer.position(), stringSize, charset);
                buffer.position(buffer.position() + stringSize);
                return onString(val);
            }
        };
        State first = new State() {
            public int getRequiredBytes() {
                return 1;
            }

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
            public int getRequiredBytes() {
                return dataSizeVal;
            }

            public State run() throws IOException {
                final Object readObject = reader.readObject(dataTopicVal, bufferArray, buffer.position(), dataSizeVal);
                buffer.position(buffer.position() + dataSizeVal);
                return onObject(dataTopicVal, readObject);
            }
        };
        State dataSize = new State() {
            public int getRequiredBytes() {
                return 4;
            }

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

        protected abstract State onObject(String dataTopicVal, Object readObject) throws IOException;
    }

    private class DataRequest extends DataRequestBase {
        @Override
        protected void handleRequest(int reqId, String dataTopicVal, Object readObject) {
            session.onRequest(reqId, dataTopicVal, readObject);
        }
    }

    private class DataRequestReply extends DataRequestBase {

        @Override
        protected void handleRequest(int reqId, String dataTopicVal, Object readObject) {
            session.onRequestReply(reqId, dataTopicVal, readObject);
        }
    }

    private abstract class DataRequestBase {
        int reqId;

        DataReader data = new DataReader() {
            @Override
            protected State onObject(String dataTopicVal, Object readObject) {
                try {
                    handleRequest(reqId, dataTopicVal, readObject);
                } catch (Exception failed) {
                    session.onHandlerException(failed);
                }
                return root;
            }
        };

        protected abstract void handleRequest(int reqId, String dataTopicVal, Object readObject);

        State reqIdSt = new State() {
            public int getRequiredBytes() {
                return 4;
            }

            public State run() {
                reqId = buffer.getInt();
                return data.first.first;
            }
        };
    }

}
