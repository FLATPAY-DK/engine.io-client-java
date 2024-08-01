package io.socket.engineio.listener;

import java.util.AbstractMap;
import java.util.function.Consumer;

public class OnceListener implements Listener {

	public final String event;
	public final Listener fn;
	private final Consumer<AbstractMap.SimpleEntry<String,Listener>> listenerRemover;

	public OnceListener(
			String event,
			Listener fn,
			Consumer<AbstractMap.SimpleEntry<String,Listener>> listenerRemover
	) {
		this.event = event;
		this.fn = fn;
		this.listenerRemover = listenerRemover;
	}

	@Override
	public void call(Object... args) {
		listenerRemover.accept(new AbstractMap.SimpleEntry<>(this.event, this));
		this.fn.call(args);
	}
}
