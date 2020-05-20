package webshop
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat, RootJsonWriter}
import webshop.user.UserRepository.ActionPerformed


trait JsonFormats extends DefaultJsonProtocol with SprayJsonSupport {
  import DefaultJsonProtocol._
  import webshop.user.UserRepository._

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(uuid) => UUID.fromString(uuid)
        case _              => throw new DeserializationException("Expected hexadecimal UUID string")
      }
    }
  }

//  implicit val userIdentifierJsonFormat = jsonFormat1(UserIdentifier))
  implicit object userIdentifierJsonFormat extends RootJsonFormat[UserIdentifier] {
  def write(identifier: UserIdentifier) = JsObject("user_id" -> JsString(identifier.userId.toString))
  def read(value: JsValue): UserIdentifier = value match {
    case JsString(uuid) => UserIdentifier(UUID.fromString(uuid))
    case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }
//  implicit val userIdentifierJsonFormat = jsonFormat1(UserIdentifier)

//  implicit val userJsonFormat = jsonFormat2(User)
  implicit object UserJsonFormat extends RootJsonFormat[User]{
    def write(user: User) = JsObject("user_id" -> JsString(user.userId.userId.toString), "credit" -> JsNumber(user.credit))
    def read(value: JsValue) = {
      value.asJsObject.getFields("user_id", "credit") match {
        case Seq(JsString(userId), JsNumber(credit)) => User(UserIdentifier(UUID.fromString(userId)), credit.toLong)
        case _ => throw DeserializationException("User expected")
      }
    }
  }
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
