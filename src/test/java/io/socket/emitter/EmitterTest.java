package io.socket.emitter;

import io.socket.engineio.listener.Listener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class EmitterTest {

    @Test
    public void on() {
        Emitter emitter = new Emitter();
        final List<Object> calls = new ArrayList<Object>();

        emitter.on("foo", args -> {
            calls.add("one");
            calls.add(args[0]);
        });

        emitter.on("foo", args -> {
            calls.add("two");
            calls.add(args[0]);
        });

        emitter.emit("foo", 1);
        emitter.emit("bar", 1);
        emitter.emit("foo", 2);

        assertThat(calls.toArray(), is(new Object[] {"one", 1, "two", 1, "one", 2, "two", 2}));
    }

    @Test
    public void once() {
        Emitter emitter = new Emitter();
        final List<Object> calls = new ArrayList<>();

        emitter.once("foo",
		        args -> {
		            calls.add("one");
		            calls.add(args[0]);
		        });

        emitter.emit("foo", 1);
        emitter.emit("foo", 2);
        emitter.emit("foo", 3);
        emitter.emit("bar", 1);

        assertThat(calls.toArray(), is(new Object[] {"one", 1}));
    }

    @Test
    public void off() {
        Emitter emitter = new Emitter();
        final List<Object> calls = new ArrayList<>();

        Listener one = args -> calls.add("one");
        Listener two = args -> calls.add("two");

        emitter.on("foo", one);
        emitter.on("foo", two);
        emitter.off("foo", two);

        emitter.emit("foo");

        assertThat(calls.toArray(), is(new Object[] {"one"}));
    }

    @Test
    public void offWithOnce() {
        Emitter emitter = new Emitter();
        final List<Object> calls = new ArrayList<>();

        Listener one = args -> calls.add("one");

        emitter.once("foo", one);
        emitter.off("foo", one);

        emitter.emit("foo");

        assertThat(calls.toArray(), is(new Object[] {}));
    }

    @Test
    public void offWhenCalledfromEvent() {
        final Emitter emitter = new Emitter();
        final boolean[] called = new boolean[] {false};
        final Listener b = args -> called[0] = true;
        emitter.on("tobi", args -> emitter.off("tobi", b));
        emitter.once("tobi", b);
        emitter.emit("tobi");
        assertThat(called[0], is(true));
        called[0] = false;
        emitter.emit("tobi");
        assertThat(called[0], is(false));
    }

    @Test
    public void offEvent() {
        Emitter emitter = new Emitter();
        final List<Object> calls = new ArrayList<Object>();

        Listener one = args -> calls.add("one");
        Listener two = args -> calls.add("two");

        emitter.on("foo", one);
        emitter.on("foo", two);
        emitter.off("foo");

        emitter.emit("foo");
        emitter.emit("foo");

        assertThat(calls.toArray(), is(new Object[] {}));
    }

    @Test
    public void offAll() {
        Emitter emitter = new Emitter();
        final List<Object> calls = new ArrayList<Object>();

        Listener one = args -> calls.add("one");
        Listener two = args -> calls.add("two");

        emitter.on("foo", one);
        emitter.on("bar", two);

        emitter.emit("foo");
        emitter.emit("bar");

        emitter.off();

        emitter.emit("foo");
        emitter.emit("bar");

        assertThat(calls.toArray(), is(new Object[]{"one", "two"}));
    }

    @Test
    public void listeners() {
        Emitter emitter = new Emitter();
        Listener foo = args -> {};
        emitter.on("foo", foo);
        assertThat(emitter.listeners("foo").toArray(), is(new Object[] {foo}));
    }

    @Test
    public void listenersWithoutHandlers() {
        Emitter emitter = new Emitter();
        assertThat(emitter.listeners("foo").toArray(), is(new Object[] {}));
    }

    @Test
    public void hasListeners() {
        Emitter emitter = new Emitter();
        emitter.on("foo", args -> {});
        assertThat(emitter.hasListeners("foo"), is(true));
    }

    @Test
    public void hasListenersWithoutHandlers() {
        Emitter emitter = new Emitter();
        assertThat(emitter.hasListeners("foo"), is(false));
    }
}
