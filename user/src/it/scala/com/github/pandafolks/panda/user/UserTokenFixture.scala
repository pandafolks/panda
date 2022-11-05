package com.github.pandafolks.panda.user

import com.github.pandafolks.panda.user.token.{Token, TokenService, TokenServiceImpl, TokensConfig}
import monix.connect.mongodb.client.{CollectionCodecRef, MongoConnection}
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

trait UserTokenFixture {
  private val dbName = "test"
  protected val mongoContainer: MongoDBContainer = new MongoDBContainer(
    DockerImageName.parse("mongo").withTag("4.0.10")
  )
  mongoContainer.start()

  private val settings: MongoClientSettings =
    MongoClientSettings
      .builder()
      .applyToClusterSettings(builder => {
        builder.applyConnectionString(new ConnectionString(mongoContainer.getReplicaSetUrl(dbName)))
        ()
      })
      .build()

  private val usersCol: CollectionCodecRef[User] = User.getCollection(dbName, randomString(User.USERS_COLLECTION_NAME))
  private val tokensCol: CollectionCodecRef[Token] =
    Token.getCollection(dbName, randomString(Token.TOKENS_COLLECTION_NAME))
  private val usersWithTokensConnection = MongoConnection.create2(settings, (usersCol, tokensCol))

  private val userDao: UserDao = new UserDaoImpl(usersWithTokensConnection)
  protected val userService: UserService = new UserServiceImpl(userDao)(usersWithTokensConnection)

  private val tokensConfig = TokensConfig(3)
  protected val tokenService: TokenService = new TokenServiceImpl(tokensConfig)(usersWithTokensConnection)

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get
}
