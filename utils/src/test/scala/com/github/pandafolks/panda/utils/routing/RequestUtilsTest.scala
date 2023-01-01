package com.github.pandafolks.panda.utils.routing

import com.github.pandafolks.panda.utils.http.RequestUtils
import monix.eval.Task
import org.http4s.{Header, Headers, Request}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.typelevel.ci.CIString

class RequestUtilsTest extends AnyFlatSpec {
  "getRealHost" should "return X-Forwarded-For if exists" in {
    val request = Request[Task](
      headers = Headers(
        Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "123.123.123")),
        Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
      )
    )

    RequestUtils.getRealHost(request).get should be("123.123.123")
  }

  it should "return nothing if there is no X-Forwarded-For header and host" in {
    val request = Request[Task](
      headers = Headers(
        Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
      )
    )

    RequestUtils.getRealHost(request) should be(None)
  }

  it should "return nothing if the X-Forwarded-For header is blank and there is no host" in {
    val request = Request[Task](
      headers = Headers(
        Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "  ")),
        Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
      )
    )

    RequestUtils.getRealHost(request) should be(None)
  }

  it should "return left most value if multiple present" in {
    val request = Request[Task](
      headers = Headers(
        Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:7348")),
        Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
      )
    )

    RequestUtils.getRealHost(request).get should be("203.0.113.195")

    val request2 = Request[Task](
      headers = Headers(
        Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:7348")),
        Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
      )
    )

    RequestUtils.getRealHost(request2).get should be("203.0.113.195")
  }

  it should "handle empty values in the list of values" in {
    val request = Request[Task](
      headers = Headers(
        Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "  ,  , 203.0.113.195 ,  ,2001:db8:85a3:8d3:1319:8a2e:370:7348,  ,  ")),
        Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
      )
    )

    RequestUtils.getRealHost(request).get should be("203.0.113.195")
  }

  "withHostHeader" should "construct headers with legit, one element" in {
    val output = RequestUtils.withHostHeader("127.0.0.1", 3001)

    output.headers.size should be(1)
    output.get(CIString("Host")).get.head.value should be("127.0.0.1:3001")
  }

  "withUpdatedXForwardedForHeader" should "add the host if the X-Forwarded-For does not exist" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2"))
    )
    val host = Some("123.123.123")

    val output = RequestUtils.withUpdatedXForwardedForHeader(headers, host)

    output.headers.size should be(1)
    output.get(CIString("X-Forwarded-For")).get.head.value should be("123.123.123")
  }

  it should "add the host if the X-Forwarded-For is empty" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "  "))
    )
    val host = Some("123.123.123")

    val output = RequestUtils.withUpdatedXForwardedForHeader(headers, host)

    output.headers.size should be(1)
    output.get(CIString("X-Forwarded-For")).get.head.value should be("123.123.123")
  }

  it should "add the host to the end of the X-Forwarded-For value if already present" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "123.123.124"))
    )
    val host = Some("123.123.123")

    val output = RequestUtils.withUpdatedXForwardedForHeader(headers, host)

    output.headers.size should be(1)
    output.get(CIString("X-Forwarded-For")).get.head.value should be("123.123.124, 123.123.123")
  }

  it should "add the host to the end of the X-Forwarded-For value if already present (long)" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734"))
    )
    val host = Some("123.123.123")

    val output = RequestUtils.withUpdatedXForwardedForHeader(headers, host)

    output.headers.size should be(1)
    output.get(CIString("X-Forwarded-For")).get.head.value should be("203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734, 123.123.123")
  }

  it should "return empty headers if there is no host" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734"))
    )
    val host = Option.empty

    val output = RequestUtils.withUpdatedXForwardedForHeader(headers, host)

    output.headers.size should be(0)
  }

  "adding empty headers" should "not remove any existing ones" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734"))
    )

    val output = headers ++ Headers()

    output.headers.size should be(2)
    output.get(CIString("different")).get.head.value should be("whatever2")
    output.get(CIString("X-Forwarded-For")).get.head.value should be("203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734")
  }

  "adding the header that already exist" should "override the existing header" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734"))
    )

    val output = headers ++ Headers(Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "newValue")))

    output.headers.size should be(2)
    output.get(CIString("different")).get.head.value should be("whatever2")
    output.get(CIString("X-Forwarded-For")).get.head.value should be("newValue")
  }

  "adding the new header" should "end up with having all of the old and new ones" in {
    val headers = Headers(
      Header.ToRaw.keyValuesToRaw(("different", "whatever2")),
      Header.ToRaw.keyValuesToRaw(("X-Forwarded-For", "203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734"))
    )

    val output = headers ++ Headers(Header.ToRaw.keyValuesToRaw(("blabla", "newValue")))

    output.headers.size should be(3)
    output.get(CIString("different")).get.head.value should be("whatever2")
    output.get(CIString("X-Forwarded-For")).get.head.value should be("203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:734")
    output.get(CIString("blabla")).get.head.value should be("newValue")
  }
}
