package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

@ApiStatus.Internal
sealed abstract class ImmutableStack<T extends @Nullable Object> implements Iterable<T>
    permits ImmutableStack.Cons, ImmutableStack.Nil {

    final ImmutableStack<T> prepend(T value) {
        return new Cons<>(value, this);
    }

    final ImmutableStack<T> prependAll(Iterable<? extends T> values) {
        ImmutableStack<T> result = this;
        for (T t : values) {
            result = result.prepend(t);
        }
        return result;
    }

    final @Nullable T head() {
        if (this instanceof Cons) {
            return ((Cons<T>) this).head;
        } else {
            return null;
        }
    }

    final ImmutableStack<T> tail() {
        if (this instanceof Cons) {
            return ((Cons<T>) this).tail;
        } else {
            return this;
        }
    }

    final boolean isEmpty() {
        return this instanceof Nil;
    }

    final ImmutableStack<T> reverse() {
        ImmutableStack<T> result = empty();
        for (T t : this) {
            result = result.prepend(t);
        }
        return result;
    }

    static final class Cons<T extends @Nullable Object> extends ImmutableStack<T> {
        final T head;
        final ImmutableStack<T> tail;

        Cons(T head, ImmutableStack<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Cons<?> cons)) return false;
            return Objects.equals(head, cons.head) && Objects.equals(tail, cons.tail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(head, tail);
        }
    }

    static final class Nil<T> extends ImmutableStack<T> {
        Nil() {}

        @Override
        public int hashCode() {
            return -2938;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Nil<?>;
        }
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
                if (current instanceof Cons<T> cons) {
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

@ApiStatus.Internal
final class ImmutableQueue<T extends @Nullable Object> implements Iterable<T> {
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

    @SuppressWarnings("NullAway")
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
        for (final T t : this) {
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
        if (obj instanceof ImmutableQueue<?> that) {
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
