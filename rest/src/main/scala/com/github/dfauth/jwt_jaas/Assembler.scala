package com.github.dfauth.jwt_jaas

import com.github.dfauth.jwt_jaas.jwt.User

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

object Assembler {

  def wrap[A,B](f:A => B):Assembler[A,B] = userWrap((user:User) => f)

  def userWrap[A,B](f:User => A => B):Assembler[A,B] = Assembler[A,B](f)

  def adaptFutureWithUser[A,B](f: User => A => B): User => Future[A] => Future[B] = {
    user => fa => fa.map(f(user)(_))
  }

  def adaptFuture[A,B](f: A => B): Future[A] => Future[B] = {
    fa => fa.map(f(_))
  }

}

case class Assembler[A,B](f:User => A => B)  {

  def map[C](g: B => C):Assembler[A,C] = userMap((user:User) => g)

  def userMap[C](g: User => B => C):Assembler[A,C] = {
    Assembler[A,C]((u:User) => f(u).andThen(g(u)))
  }

  def apply(u:User): A => B = f(u)
}



