package com.github.dfauth.jwt_jaas.common

import com.github.dfauth.jwt_jaas.common.ContextualPipeline._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class ContextualPipelineSpec extends FlatSpec with Matchers with LazyLogging {

  val f1: A => B = a => B(a.payload+" b")

  val fctx1 = (ctx:Ctx) => f1.compose((a:A) => A(ctx.transformPayload(a.payload)))

  val f2: B => C = b => C(b.payload+" c")

  val f3: C => D = c => D(c.payload+" d")

  // Futurized version of f3
  val ff3: C => Future[D] = c => Future[D] {
    logger.info(s"running in ${Thread.currentThread().getName}")
    f3(c)
  }

  val f4: D => E = d => E(d.payload+" e")

  // Futurized version of f4
  val ff4: D => Future[E] = d => Future[E] {
    logger.info(s"running in ${Thread.currentThread().getName}")
    f4(d)
  }

  val f5: E => F = e => F(e.payload+" f")

  val fctx3 = (ctx:Ctx) => f3.compose((c:C) => C(ctx.transformPayload(c.payload)))

  def authzn[T,U](f:T => U)(role:String):UserCtx => T => Try[U] = u => t => {
    logger.info(s"${u.u.name} has roles ${u.u.roles}")
    u.u.roles.contains(role) match {
      case true => Success[U](f(t))
      case false => Failure[U](new SecurityException(s"${u.u.name} not authorized"))
    }
  }

  val fctx4: UserCtx => C => Try[D] = authzn[C,D](f3)("admin")

  val fctx5: UserCtx => D => Try[E] = authzn[D,E](f4)("admin")

  def adapt[CTX,T,U](f: CTX => T => Try[U]): CTX => Future[T] => Future[U] = ctx => ft => {
    ft.map[U](t => f(ctx)(t) match {
      case s:Success[U] => s.value
      case f:Failure[U] => throw f.exception
    })
  }

  "a contextual pipeline" should "be able to compose functions in the context of a given object" in {

    {
      lazy val pipeline = ContextualPipeline[Ctx,A,B](fctx1)
      val b = pipeline(new Ctx())(A("the payload"))

      b.payload should be ("the payload was transformed b")
    }

    {
      // this can also be expressed as follows
      lazy val pipeline = ctxWrap[Ctx,A,B](fctx1)

      val b = pipeline(new Ctx())(A("the payload"))
      b.payload should be ("the payload was transformed b")
    }

    {
      // simpler functions may not know about the context
      lazy val pipeline = wrap[Ctx,A,B](f1)

      val b = pipeline(new Ctx())(A("the payload"))
      b.payload should be ("the payload b")
    }

    {
      // this allows us to compose functions simply
      lazy val pipeline = wrap[Ctx,A,B](f1).
        map(f2)

      val c = pipeline(new Ctx())(A("the payload"))
      c.payload should be ("the payload b c")
    }

    {
      // this allows us to compose functions simply including some needing the context
      lazy val pipeline = wrap[Ctx,A,B](f1).
        map(f2).
        mapWithContext(fctx3)

      val c = pipeline(new Ctx())(A("the payload"))
      c.payload should be ("the payload b c was transformed d")
    }

    {
      // we can also compose with futures
      lazy val pipeline = wrap[Ctx,A,B](f1).
        map(f2).
        map(ff3)

      val d = pipeline(new Ctx())(A("the payload"))
      Await.result(d, 2.seconds).payload should be ("the payload b c d")
    }

    {
      // we can also compose with futures and adapt functions to futures
      lazy val pipeline = wrap[Ctx,A,B](f1).
        map(f2).
        map(ff3).
        map(adaptFuture(f4))

      val e:Future[E] = pipeline(new Ctx())(A("the payload"))
      Await.result(e, 2.seconds).payload should be ("the payload b c d e")
    }

    {
      // we can also compose with futures of futures and adapt functions to futures
      lazy val pipeline:ContextualPipeline[Ctx, A, Future[Future[E]]] = wrap[Ctx,A,B](f1).
        map(f2).
        map(ff3).
        map(adaptFuture(ff4))
        //map(adaptFuture(f5))

      // Work in progress
      val e:Future[Future[E]] = pipeline(new Ctx())(A("the payload"))
      Await.result(Await.result(e, 2.seconds), 2.seconds).payload should be ("the payload b c d e")
    }

    {
      // the most useful example of this is where the context varies, such as the user context
      lazy val pipeline = wrap[UserCtx,A,B](f1).
        map(f2).
        mapWithContext(fctx3)

      val c = pipeline(UserCtx(User("fred")))(A("the payload"))
      c.payload should be ("the payload b c was transformed by fred d")
      val c1 = pipeline(UserCtx(User("wilma")))(A("the payload"))
      c1.payload should be ("the payload b c was transformed by wilma d")
      val c2 = pipeline(UserCtx(User("barney")))(A("the payload"))
      c2.payload should be ("the payload b c was transformed by barney d")
    }

    {
      // this is most useful for authorisation
      lazy val pipeline = wrap[UserCtx,A,B](f1).
        map(f2).
        mapWithContext(fctx4)

      val c = pipeline(UserCtx(User("fred", Set("admin"))))(A("the payload"))
      c match {
        case Success(c) => c.payload should be ("the payload b c d")
        case Failure(t) => fail(t.getMessage, t)
      }

      val c1 = pipeline(UserCtx(User("wilma")))(A("the payload"))
      c1 match {
        case Success(c) => fail("expected authorization failure")
        case Failure(t) => t.getMessage should be ("wilma not authorized")
      }
    }

    {
      // but it also allows this user context infomation to be propagated across Futures (ie. in different threads)
      lazy val pipeline = wrap[UserCtx,A,B](f1).
        map[C](f2).
        map[Future[D]](ff3).
        mapWithContext[Future[E]](adapt[UserCtx,D,E](fctx5))

      val fe = pipeline(UserCtx(User("fred", Set("admin"))))(A("the payload"))
      Await.result(fe, 2.seconds).payload should be ("the payload b c d e")

      val fe1 = pipeline(UserCtx(User("wilma")))(A("the payload"))
      fe1.recover {
        case t: Throwable => t.getMessage should be("the payload b c d e")
      }.isCompleted should be (false)
    }

  }

  case class User(name:String,roles: Set[String] = Set.empty[String])
  class Ctx() {
    def transformPayload(payload:String):String = payload+" was transformed"
  }
  case class UserCtx(u:User) extends Ctx {
    override def transformPayload(payload:String):String = payload+s" was transformed by ${u.name}"
  }
  case class A(payload:String)
  case class B(payload:String)
  case class C(payload:String)
  case class D(payload:String)
  case class E(payload:String)
  case class F(payload:String)
}
