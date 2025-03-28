package org.funfix.tasks.scala

import org.funfix.tasks.jvm.CompletionCallback as JavaCompletionCallback

type CompletionCallback[-A] = Outcome[A] => Unit

extension [A](cb: JavaCompletionCallback[_ >: A]) {
  def asScala: CompletionCallback[A] = {
    case Outcome.Success(value) => cb.onSuccess(value)
    case Outcome.Failure(ex) => cb.onFailure(ex)
    case Outcome.Cancellation => cb.onCancellation()
  }
}

extension [A](cb: CompletionCallback[A]) {
  def asJava: JavaCompletionCallback[_ >: A] =
    new JavaCompletionCallback[A] {
      def onSuccess(value: A): Unit = cb(Outcome.Success(value))
      def onFailure(ex: Throwable): Unit = cb(Outcome.Failure(ex))
      def onCancellation(): Unit = cb(Outcome.Cancellation)
    }
}
