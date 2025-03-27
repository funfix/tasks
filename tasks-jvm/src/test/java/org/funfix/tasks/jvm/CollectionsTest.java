package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CollectionsTest {
    @Test
    void listEqualityTest() {
        ImmutableStack<String> list1 = ImmutableStack.empty();
        ImmutableStack<String> list2 = ImmutableStack.empty();
        final var elems = new String[] { "a", "b", "c" };

        for (String elem : elems) {
            list1 = list1.prepend(elem);
            list2 = list2.prepend(elem);
        }

        assertEquals(list1, list2);
        assertNotEquals(list1, list2.reverse());
        assertEquals(list1.reverse(), list2.reverse());
    }

    @Test
    void queueToList() {
        ImmutableQueue<String> queue = ImmutableQueue.empty();
        queue = queue.enqueue("1");
        queue = queue.enqueue("2");
        queue = queue.enqueue("3");
        queue = queue.preOptimize();
        queue = queue.enqueue("4");
        queue = queue.enqueue("5");
        queue = queue.enqueue("6");

        assertEquals(
            "ImmutableStack(1, 2, 3, 4, 5, 6)",
            queue.toList().toString()
        );

        queue = queue.dequeue();
        queue = queue.dequeue();
        assertEquals(
            "ImmutableStack(3, 4, 5, 6)",
            queue.toList().toString()
        );
    }

    @Test
    void queueEqualityTest() {
        ImmutableQueue<String> list1 = ImmutableQueue.empty();
        ImmutableQueue<String> list2 = ImmutableQueue.empty();
        ImmutableQueue<String> list3 = ImmutableQueue.empty();
        ImmutableQueue<String> list4 = ImmutableQueue.empty();
        final var elems = new String[] { "a", "b", "c" };

        list3 = list3.enqueue("0");
        for (String elem : elems) {
            list1 = list1.enqueue(elem);
            list2 = list2.enqueue(elem);
            list3 = list3.enqueue(elem);
            list4 = list4.enqueue(elem);
        }
        list4 = list4.enqueue("d");

        assertEquals(list1, list2);
        assertEquals(list1.hashCode(), list2.hashCode());
        assertNotEquals(list1, list3);
        assertNotEquals(list1.hashCode(), list3.hashCode());
        assertNotEquals(list1, list4);
        assertNotEquals(list1.hashCode(), list4.hashCode());
        assertNotEquals(list3, list4);
        assertNotEquals(list3.hashCode(), list4.hashCode());

        assertEquals(
            "ImmutableQueue(a, b, c)",
            list1.toString()
        );
        assertEquals(
            "ImmutableQueue(a, b, c)",
            list2.toString()
        );
        assertEquals(
            "ImmutableQueue(0, a, b, c)",
            list3.toString()
        );
        assertEquals(
            "ImmutableQueue(a, b, c, d)",
            list4.toString()
        );
    }
}
