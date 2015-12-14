package org.jetlang.remote.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public class JetlangRemotingProtocol {

    public ByteBuffer buffer;
    public byte[] bufferArray = new byte[1024 * 8];
    private final Handler session;
    private final DataRequest dataRequest = new DataRequest();
    private final DataReader d = new DataReader() {
        @Override
        protected State onObject(String dataTopicVal, Object readObject) throws IOException {
            session.onMessage(dataTopicVal, readObject);
            return root;
        }
    };
    public final State root = new State() {
        public int getRequiredBytes() {
            return 1;
        }

        public State run() throws IOException {
            int read = buffer.get();
            switch (read) {
                case MsgTypes.Heartbeat:
                    session.onHb();
                    return this;
                case MsgTypes.Subscription:
                    return subRequest.first;
                case MsgTypes.Unsubscribe:
                    return unsubRequest.first;
                case MsgTypes.Disconnect:
                    session.onLogout();
                    return this;
                case MsgTypes.Data:
                    return d.first.first;
                case MsgTypes.DataRequest:
                    return dataRequest.reqIdSt;
                default:
                    session.onUnknownMessage(read);
                    return this;
            }
        }
    };
    private final StringState subRequest = new StringState() {
        @Override
        protected State onString(String val) throws IOException {
            session.onSubscriptionRequest(val);
            return root;
        }
    };
    private final StringState unsubRequest = new StringState() {
        @Override
        protected State onString(String val) throws IOException {
            session.onUnsubscribeRequest(val);
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
    }

    public JetlangRemotingProtocol(Handler session, ObjectByteReader reader) {
        this.session = session;
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
                String val = new String(bufferArray, buffer.position(), stringSize);
                buffer.position(buffer.position() + stringSize);
                return onString(val);
            }
        };
        State first = new State() {
            public int getRequiredBytes() {
                return 1;
            }

            public State run() throws IOException {
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

            public State run() throws IOException {
                dataSizeVal = buffer.getInt();
                return dataSizeRead;
            }
        };
        StringState first = new StringState() {
            @Override
            protected State onString(String val) throws IOException {
                dataTopicVal = val;
                return dataSize;
            }
        };

        protected abstract State onObject(String dataTopicVal, Object readObject) throws IOException;
    }

    private class DataRequest {
        int reqId;

        DataReader data = new DataReader() {
            @Override
            protected State onObject(String dataTopicVal, Object readObject) throws IOException {
                session.onRequest(reqId, dataTopicVal, readObject);
                return root;
            }
        };

        State reqIdSt = new State() {
            public int getRequiredBytes() {
                return 4;
            }

            public State run() throws IOException {
                reqId = buffer.getInt();
                return data.first.first;
            }
        };
    }

}
