# Vocabulary

* `execute` is used for executing a function or a task that may suspend side effects. The project uses `execute` instead of `run` or other synonyms. This is similar to Java's `Executor#execute`.
* `join` is used for waiting for the completion of an already started fiber / thread, but without awaiting a final result.
