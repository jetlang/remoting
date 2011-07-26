package org.jetlang.remote.acceptor;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 2:11 PM
 */
public interface ClientPublisher {

    void publishToAllSubscribedClients(String topic, Object msg);

}
