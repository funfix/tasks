package org.funfix.tasks.scala

import org.funfix.tasks.jvm.TaskExecutors

import scala.concurrent.ExecutionContext

opaque type TaskExecutor <: Executor = Executor

object TaskExecutor {
  def apply(executor: Executor): TaskExecutor = executor

  lazy val compute: TaskExecutor =
    TaskExecutor(ExecutionContext.global)

  lazy val blockingIO: TaskExecutor =
    TaskExecutor(TaskExecutors.sharedBlockingIO())

  lazy val trampoline: TaskExecutor =
    TaskExecutor(TaskExecutors.trampoline())

  object Givens {
    given compute: TaskExecutor = 
      TaskExecutor.compute
    given blockingIO: TaskExecutor = 
      TaskExecutor.blockingIO
  }
}
