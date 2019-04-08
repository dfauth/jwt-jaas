package com.github.dfauth.jwt_jaas.authzn

import Actions.using
import Assertions.WasRunAssertion
import PrincipalType.{ROLE, USER}
import TestUtils.TestAction._
import TestUtils.{TestAction, TestPermission}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class AuthorizationSpec
    extends FlatSpec
    with Matchers
    with LazyLogging {

  "the authorization policy" should "also have an idiomatic scala interface" in {

    val subject = new ImmutableSubject(USER.of("fred"), ROLE.of("admin"), ROLE.of("user"))
    val perm = new TestPermission("/a/b/c/d", using(classOf[TestAction]).parse("*"))
    val directive = new Directive(ROLE.of("superuser"), perm)
    val policy = AuthorizationPolicyMonad(directive)

    val testPerm = new TestPermission("/a/b/c/d/e/f/g", READ)

    {
      policy.permit(subject, testPerm) {
        new WasRunAssertion().run
      } match {
        case Success(a) => {
          logger.error(s"expected authzn failure, received: ${a}")
          fail(s"expected authzn failure, received: ${a}")
        }
        case Failure(t) => {
          t.getMessage should be ("user: fred roles: [admin, user] is not authorized to perform actions [READ] on resource /a/b/c/d/e/f/g")
        }
      }
    }

    {
      // add super user role
      val subject1 = subject.`with`(ROLE.of("superuser"))
      policy.permit(subject1, testPerm) {
        new WasRunAssertion().run
      } match {
        case Success(a) => a.wasRun() should be (true)
        case Failure(t) => fail(s"Expected assertion to run, instead received exception: ${t}", t)
      }
    }
  }
}
