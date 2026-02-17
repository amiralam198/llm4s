package org.llm4s.llmconnect.provider

import org.llm4s.error.{ AuthenticationError, RateLimitError, ServiceError }
import org.llm4s.llmconnect.config.OpenAIConfig
import org.llm4s.llmconnect.model.{ CompletionOptions, Conversation, UserMessage }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ ByteArrayInputStream, InputStream }
import java.net.URI
import java.net.http.{ HttpClient, HttpHeaders, HttpRequest, HttpResponse }
import java.util.Optional
import java.util.concurrent.{ CompletableFuture, Executor }

final class OpenRouterClientStreamingErrorSpec extends AnyFlatSpec with Matchers {

  private val testConfig: OpenAIConfig = OpenAIConfig.fromValues(
    modelName = "openai/gpt-4",
    apiKey = "invalid-key",
    organization = None,
    baseUrl = "https://openrouter.ai/api/v1"
  )

  private val conversation: Conversation = Conversation(Seq(UserMessage("hello")))

  "OpenRouterClient.streamComplete" should "return AuthenticationError on 401 (no throw)" in {
    val response: HttpResponse[InputStream] = new StubHttpResponse(
      status = 401,
      responseUri = URI.create("https://openrouter.ai/api/v1/chat/completions"),
      responseBody = new ByteArrayInputStream("unauthorized".getBytes("UTF-8")): InputStream
    )

    val client = new OpenRouterClient(testConfig, httpClient = new StubHttpClient(response))

    var chunks = 0
    val result = client.streamComplete(conversation, CompletionOptions(), _ => chunks += 1)

    result.left.toOption.get shouldBe a[AuthenticationError]
    chunks shouldBe 0
  }

  it should "return RateLimitError on 429 (no throw)" in {
    val response: HttpResponse[InputStream] = new StubHttpResponse(
      status = 429,
      responseUri = URI.create("https://openrouter.ai/api/v1/chat/completions"),
      responseBody = new ByteArrayInputStream("rate limited".getBytes("UTF-8")): InputStream
    )

    val client = new OpenRouterClient(testConfig, httpClient = new StubHttpClient(response))

    val result = client.streamComplete(conversation, CompletionOptions(), _ => ())

    result.left.toOption.get shouldBe a[RateLimitError]
  }

  it should "return ServiceError on non-200 non-401 non-429 (no throw)" in {
    val response: HttpResponse[InputStream] = new StubHttpResponse(
      status = 500,
      responseUri = URI.create("https://openrouter.ai/api/v1/chat/completions"),
      responseBody = new ByteArrayInputStream("server error".getBytes("UTF-8")): InputStream
    )

    val client = new OpenRouterClient(testConfig, httpClient = new StubHttpClient(response))

    val result = client.streamComplete(conversation, CompletionOptions(), _ => ())

    result.left.toOption.get shouldBe a[ServiceError]
  }

  final private class StubHttpResponse[T](
    status: Int,
    responseUri: URI,
    responseBody: T
  ) extends HttpResponse[T] {
    private val req: HttpRequest = HttpRequest.newBuilder(responseUri).build()

    override def statusCode(): Int = status

    override def request(): HttpRequest = req

    override def previousResponse(): Optional[HttpResponse[T]] = Optional.empty()

    override def headers(): HttpHeaders =
      HttpHeaders.of(java.util.Map.of[String, java.util.List[String]](), (_: String, _: String) => true)

    override def body(): T = responseBody

    override def sslSession(): Optional[javax.net.ssl.SSLSession] = Optional.empty()

    override def uri(): URI = responseUri

    override def version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
  }

  final private class StubHttpClient(response: HttpResponse[InputStream]) extends HttpClient {
    override def cookieHandler(): Optional[java.net.CookieHandler] = Optional.empty()

    override def connectTimeout(): Optional[java.time.Duration] = Optional.empty()

    override def followRedirects(): HttpClient.Redirect = HttpClient.Redirect.NEVER

    override def proxy(): Optional[java.net.ProxySelector] = Optional.empty()

    override def sslContext(): javax.net.ssl.SSLContext = javax.net.ssl.SSLContext.getDefault

    override def sslParameters(): javax.net.ssl.SSLParameters = new javax.net.ssl.SSLParameters()

    override def authenticator(): Optional[java.net.Authenticator] = Optional.empty()

    override def version(): HttpClient.Version = HttpClient.Version.HTTP_1_1

    override def executor(): Optional[Executor] = Optional.empty()

    override def send[T](
      request: HttpRequest,
      responseBodyHandler: HttpResponse.BodyHandler[T]
    ): HttpResponse[T] = response.asInstanceOf[HttpResponse[T]]

    override def sendAsync[T](
      request: HttpRequest,
      responseBodyHandler: HttpResponse.BodyHandler[T]
    ): CompletableFuture[HttpResponse[T]] =
      CompletableFuture.completedFuture(send(request, responseBodyHandler))

    override def sendAsync[T](
      request: HttpRequest,
      responseBodyHandler: HttpResponse.BodyHandler[T],
      pushPromiseHandler: HttpResponse.PushPromiseHandler[T]
    ): CompletableFuture[HttpResponse[T]] =
      CompletableFuture.completedFuture(send(request, responseBodyHandler))

    override def newWebSocketBuilder(): java.net.http.WebSocket.Builder =
      java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()
  }
}
