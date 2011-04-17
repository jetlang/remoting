package com.jetlang.remote;

import org.jetlang.core.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallbackList<T> implements Callback<T> {

    public final List<T> received = Collections.synchronizedList(new ArrayList<T>());

    public void onMessage(T t) {
        received.add(t);
    }

    public static <T> CallbackList<T> create() {
        return new CallbackList<T>();
    }
}
