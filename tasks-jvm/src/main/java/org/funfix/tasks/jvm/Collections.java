package org.funfix.tasks.jvm;

import lombok.EqualsAndHashCode;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

@NullMarked
abstract class ImmutableStack<T> implements Iterable<T> {
    protected ImmutableStack() {}

    ImmutableStack<T> prepend(T value) {
        return new Cons<>(value, this);
    }

    ImmutableStack<T> prependAll(Iterable<? extends T> values) {
        ImmutableStack<T> result = this;
        for (T t : values) {
            result = result.prepend(t);
        }
        return result;
    }

    @Nullable T head() {
        if (this instanceof Cons) {
            return ((Cons<T>) this).head;
        } else {
            return null;
        }
    }

    ImmutableStack<T> tail() {
        if (this instanceof Cons) {
            return ((Cons<T>) this).tail;
        } else {
            return this;
        }
    }

    boolean isEmpty() {
        return this instanceof Nil;
    }

    ImmutableStack<T> reverse() {
        ImmutableStack<T> result = empty();
        for (T t : this) {
            result = result.prepend(t);
        }
        return result;
    }

    @EqualsAndHashCode(callSuper = false)
    private static final class Cons<T> extends ImmutableStack<T> {
        final T head;
        final ImmutableStack<T> tail;

        Cons(T head, ImmutableStack<T> tail) {
            this.head = head;
            this.tail = tail;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    private static final class Nil<T> extends ImmutableStack<T> {
        Nil() {}
    }

    static <T> ImmutableStack<T> empty() {
        return new Nil<>();
    }

    @Override
    public Iterator<T> iterator() {
        final var start = this;
        return new Iterator<>() {
            private ImmutableStack<T> current = start;

            @Override
            public boolean hasNext() {
                return current instanceof Cons;
            }

            @Override
            public T next() {
                if (current instanceof Cons) {
                    final var cons = (Cons<T>) current;
                    current = cons.tail;
                    return cons.head;
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("ImmutableStack(");
        var isFirst = true;
        for (final var t : this) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(t);
        }
        sb.append(")");
        return sb.toString();
    }
}

final class ImmutableQueue<T> implements Iterable<T> {
    private final ImmutableStack<T> toEnqueue;
    private final ImmutableStack<T> toDequeue;

    private ImmutableQueue(ImmutableStack<T> toEnqueue, ImmutableStack<T> toDequeue) {
        this.toEnqueue = toEnqueue;
        this.toDequeue = toDequeue;
    }

    ImmutableQueue<T> enqueue(T value) {
        return new ImmutableQueue<>(toEnqueue.prepend(value), toDequeue);
    }

    ImmutableStack<T> toList() {
        return toEnqueue.reverse().prependAll(toDequeue.reverse());
    }

    ImmutableQueue<T> preOptimize() {
        if (toDequeue.isEmpty()) {
            return new ImmutableQueue<>(ImmutableStack.empty(), toEnqueue.reverse());
        } else {
            return this;
        }
    }

    T peek() throws NoSuchElementException {
        if (!toDequeue.isEmpty()) {
            return toDequeue.head();
        } else if (!toEnqueue.isEmpty()) {
            return toEnqueue.reverse().head();
        } else {
            throw new NoSuchElementException("peek() on empty queue");
        }
    }

    ImmutableQueue<T> dequeue() {
        if (!toDequeue.isEmpty()) {
            return new ImmutableQueue<>(toEnqueue, toDequeue.tail());
        } else if (!toEnqueue.isEmpty()) {
            return new ImmutableQueue<>(ImmutableStack.empty(), toEnqueue.reverse().tail());
        } else {
            return this;
        }
    }

    boolean isEmpty() {
        return toEnqueue.isEmpty() && toDequeue.isEmpty();
    }

    ImmutableQueue<T> filter(Predicate<? super T> predicate) {
        ImmutableQueue<T> result = empty();
        for (T t : this) {
            if (predicate.test(t)) {
                result = result.enqueue(t);
            }
        }
        return result;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private ImmutableQueue<T> current = ImmutableQueue.this;

            @Override
            public boolean hasNext() {
                return !current.isEmpty();
            }

            @Override
            public T next() {
                current = current.preOptimize();
                if (current.isEmpty()) {
                    throw new NoSuchElementException("next() on empty queue");
                }
                final var value = current.peek();
                current = current.dequeue();
                return value;
            }
        };
    }


    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("ImmutableQueue(");
        var isFirst = true;
        for (val t : this) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(t);
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ImmutableQueue) {
            final var that = (ImmutableQueue<?>) obj;
            return toList().equals(that.toList());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return toList().hashCode();
    }

    static <T> ImmutableQueue<T> empty() {
        return new ImmutableQueue<>(ImmutableStack.empty(), ImmutableStack.empty());
    }
}
