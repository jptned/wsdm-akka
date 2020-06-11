package microservice.types

import java.nio.ByteBuffer
import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.cluster.ddata.protobuf.{ReplicatedDataSerializer, SerializationSupport}
import akka.serialization.Serializer

class UserSerializer(val system: ExtendedActorSystem) extends Serializer with SerializationSupport {
  override def identifier: Int = 1000

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case u: UserType => userToBinary(u)
    case _ => throw new IllegalArgumentException()
  }
  
  def userToBinary(user: UserType): Array[Byte] = {
    val bb = ByteBuffer.wrap(new Array[Byte](16))
    val uuid = UUID.fromString(user.user_id)
    bb.putLong(uuid.getMostSignificantBits)
    bb.putLong(uuid.getLeastSignificantBits)
    bb.array() ++ new ReplicatedDataSerializer(system).pncounterToProto(user.credit).toByteArray
  }

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val bb = ByteBuffer.wrap(bytes)
    val uuid = new UUID(bb.getLong, bb.getLong)
    val counter = new ReplicatedDataSerializer(system).pncounterFromBinary(bb.array().slice(bb.position(), bb.array().length))
    new UserType(uuid.toString, counter)
  }
}
