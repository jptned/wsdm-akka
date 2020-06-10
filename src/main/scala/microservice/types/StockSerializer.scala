package microservice.types

import java.nio.ByteBuffer
import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.cluster.ddata.protobuf.{ReplicatedDataSerializer, SerializationSupport}
import akka.serialization.Serializer

class StockSerializer(val system: ExtendedActorSystem) extends Serializer with SerializationSupport {
  override def identifier: Int = 1001

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case s: StockType => stockToBinary(s)
    case _ => throw new IllegalArgumentException()
  }
  
  def stockToBinary(stock: StockType): Array[Byte] = {
    val bb = ByteBuffer.wrap(new Array[Byte](16+8))
    val uuid = UUID.fromString(stock.item_id)
    bb.putLong(uuid.getMostSignificantBits)
    bb.putLong(uuid.getLeastSignificantBits)
    bb.putLong(stock.price)
    bb.array() ++ new ReplicatedDataSerializer(system).pncounterToProto(stock.stock).toByteArray
  }

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val bb = ByteBuffer.wrap(bytes)
    val uuid = new UUID(bb.getLong, bb.getLong)
    val price = bb.getLong
    val counter = new ReplicatedDataSerializer(system).pncounterFromBinary(bb.array().slice(bb.position(), bb.array().length))
    new StockType(uuid.toString, counter, price)
  }
}
