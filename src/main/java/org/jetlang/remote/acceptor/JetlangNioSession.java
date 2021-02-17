package org.jetlang.remote.acceptor;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.client.SendBuffer;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.web.NioWriter;
import org.jetlang.web.SendResult;

public class JetlangNioSession<R, W> extends JetlangBaseSession<R, W> implements JetlangMessageHandler<R> {

    private final NioJetlangSendFiber.ChannelState channel;
    private final NioJetlangSendFiber<W> sendFiber;
    private final ErrorHandler<R> errorHandler;

    public interface ErrorHandler<T> {

        void onUnhandledReplyMsg(int reqId, String dataTopicVal, T readObject);

        void onUnknownMessage(int read);

        void onHandlerException(Exception failed);
    }

    public JetlangNioSession(NioFiber fiber, NioJetlangSendFiber<W> sendFiber, NioJetlangRemotingClientFactory.Id id, ErrorHandler<R> errorHandler,
                             NioWriter writer) {
        super(id);
        this.errorHandler = errorHandler;
        this.channel = new NioJetlangSendFiber.ChannelState(writer, id, fiber);
        this.sendFiber = sendFiber;
        this.sendFiber.onNewSession(this.channel);
    }

    NioWriter getWriter(){
        return channel.channel;
    }

    public SendResult send(SendBuffer buffer){
        return getWriter().send(buffer.getBuffer());
    }

    @Override
    public void onHandlerException(Exception failed) {
        errorHandler.onHandlerException(failed);
    }

    public void sendHb() {
        sendFiber.sendIntAsByte(channel, MsgTypes.Heartbeat);
    }

    @Override
    public void onLogout() {
        sendFiber.handleLogout(channel);
        Logout.publish(new LogoutEvent());
    }

    @Override
    public void onSubscriptionRequest(String topic) {
        sendFiber.onSubscriptionRequest(topic, channel);
        SubscriptionRequest.publish(new SessionTopic(topic, this));
    }

    @Override
    public void onUnsubscribeRequest(String topic) {
        UnsubscribeRequest.publish(topic);
        sendFiber.onUnsubscribeRequest(topic, channel);
    }

    @Override
    public void publish(String topic, W msg) {
        sendFiber.publish(channel, topic, msg);
    }

    @Override
    public void disconnect() {
        channel.closeOnNioFiber();
    }

    @Override
    public void publish(byte[] data) {
        sendFiber.publishBytes(channel, data);
    }

    @Override
    public void reply(int reqId, String replyTopic, W replyMsg) {
        sendFiber.reply(channel, reqId, replyTopic, replyMsg);
    }

    @Override
    public void onRequestReply(int reqId, String dataTopicVal, R readObject) {
        errorHandler.onUnhandledReplyMsg(reqId, dataTopicVal, readObject);
    }

    @Override
    public void publishIfSubscribed(String topic, byte[] data) {
        sendFiber.publishIfSubscribed(channel, topic, data);
    }

    @Override
    public void onClose(SessionCloseEvent sessionCloseEvent) {
        sendFiber.handleClose(channel);
        super.onClose(sessionCloseEvent);
    }

    @Override
    public void onUnknownMessage(int read) {
        errorHandler.onUnknownMessage(read);
    }
}
