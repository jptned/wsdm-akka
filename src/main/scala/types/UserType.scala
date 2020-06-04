package types

import akka.cluster.ddata.{Key, PNCounter, ReplicatedData, ReplicatedDataSerialization, SelfUniqueAddress}

object UserType {
  def create(user_id: String) : UserType = new UserType(user_id, PNCounter.empty)
}

final case class NotEnoughCreditException() extends Exception

final class UserType(val user_id: String, val credit: PNCounter) extends ReplicatedData {
  override type T = UserType
  
  def increment(n: Long)(implicit node: SelfUniqueAddress): UserType = {
    copy(credit = this.credit.increment(n))
  }
  
  def decrement(n: Long)(implicit node: SelfUniqueAddress): UserType = {
    if (this.credit.value < n) throw NotEnoughCreditException()
    copy(credit = this.credit.decrement(n))
  }

  override def merge(that: UserType): UserType = {
    copy(credit = that.credit.merge(this.credit))
  }
  
  private def copy(user_id: String = this.user_id, credit: PNCounter = this.credit): UserType =
    new UserType(user_id, credit)
  
  def creditValue: BigInt = credit.value
}

@SerialVersionUID(1L)
final case class UserTypeKey(_id: String) extends Key[UserType](_id) with ReplicatedDataSerialization
