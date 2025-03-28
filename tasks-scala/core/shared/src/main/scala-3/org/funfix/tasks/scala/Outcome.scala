package org.funfix.tasks.scala

enum Outcome[+A] {
  case Success(value: A)
  case Failure(exception: Throwable)
  case Cancellation
}
