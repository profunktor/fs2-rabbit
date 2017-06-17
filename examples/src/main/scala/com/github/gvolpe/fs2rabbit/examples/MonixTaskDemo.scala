package com.github.gvolpe.fs2rabbit.examples

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

object MonixTaskDemo extends GenericDemo[Task] with App
