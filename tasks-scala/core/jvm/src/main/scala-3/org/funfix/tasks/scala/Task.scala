package org.funfix.tasks.scala

import org.funfix.tasks.jvm.Task as JavaTask

opaque type Task[+A] = JavaTask[_ <: A]

object Task {
  def apply[A](underlying: JavaTask[_ <: A]): Task[A] = underlying

  def fromAsync[A](f: (Executor, CompletionCallback[A]) => Cancellable): Task[A] =
    Task[A](JavaTask.fromAsync { (ec, cb) =>
      f(ec, cb.asScala)
    })

  extension [A](task: Task[A]) {
    def asPlatform: JavaTask[_ <: A] = task

    def unsafeRunAsync(ec: Executor)(cb: CompletionCallback[A]): Cancellable =
      unsafeRunAsync(cb)(using TaskExecutor(ec))

    def unsafeRunAsync(cb: CompletionCallback[A])(using ec: TaskExecutor): Cancellable =
      task.asPlatform.runAsync(ec, cb.asJava)
  }
}
