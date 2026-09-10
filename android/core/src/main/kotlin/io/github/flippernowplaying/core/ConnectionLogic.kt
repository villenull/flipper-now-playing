package io.github.flippernowplaying.core

/** The platform never gets a second operation until the first completes or its connection closes. */
class GattOperationGate {
 data class Ticket(val generation: Int,val serial: Long,val name: String,val deadline: Long)
 var current: Ticket?=null;private set
 private var serial=0L
 fun begin(generation: Int,name: String,now: Long,timeout: Long): Ticket {
  check(current==null) { "Parallel GATT operation" }
  return Ticket(generation,++serial,name,now+timeout).also { current=it }
 }
 fun complete(generation: Int,name: String): Boolean {
  val ticket=current?:return false
  if(ticket.generation!=generation||ticket.name!=name)return false
  current=null;return true
 }
 fun expired(ticket: Ticket,now: Long)=current==ticket&&now>=ticket.deadline
 fun reset() { current=null }
}
object PlayerSelector {
 fun <T> choose(eligible: List<T>,current: T?,playing: (T)->Boolean): T? =
  eligible.firstOrNull { it==current&&playing(it) }?:eligible.firstOrNull(playing)?:eligible.firstOrNull { it==current }?:eligible.firstOrNull()
}

class CounterExhausted: IllegalStateException("Connection counter exhausted")
class ConnectionCounter(private var value: Long=0) {
 fun next(): Long { if(value==0xffffffffL)throw CounterExhausted();return ++value }
 fun reset() { value=0 }
}
