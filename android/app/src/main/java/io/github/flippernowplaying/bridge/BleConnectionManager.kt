package io.github.flippernowplaying.bridge

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.os.*
import io.github.flippernowplaying.core.*
import java.security.SecureRandom
import java.util.UUID

/** All state is owned by the service's serial Handler. Callbacks copy borrowed data. */
@Suppress("DEPRECATION") // Legacy value setters are used only below API 33.
@SuppressLint("MissingPermission") // Calls are guarded at entry; every actor operation catches revocation.
class BleConnectionManager(private val context: Context,private val h: Handler,private val address: String,
 private val status: (String)->Unit,private val synced: ()->Unit,private val incoming: (Frame,Long)->Unit) {
 private val adapter=context.getSystemService(BluetoothManager::class.java).adapter
 private var gatt: BluetoothGatt?=null
 private var rx: BluetoothGattCharacteristic?=null
 private var tx: BluetoothGattCharacteristic?=null
 private var stopped=true; private var generation=0; private val operations=GattOperationGate()
 private val pending: String get()=operations.current?.name.orEmpty()
 private var stage="STOPPED"; private var retry=0
 private val parser=StreamDecoder(); private val publisher=Publisher()
 private var active: ByteArray?=null; private var offset=0; private var sent: (() -> Unit)?=null
 private var session=0L; private var id=0L; private var lastId=0L; private var lastPeer=0L; private var errorBase=0; private var errorAt=0L
 private val commandIds=ArrayDeque<Long>()
 private var snapshotId=0L; private var snapshotSeq=0L
 var ready=false; private set
 private fun now()=SystemClock.elapsedRealtime()
 private fun state(s: String) { stage=s; status(s) }
 private fun safe(block: ()->Unit) { try { block() } catch(_: SecurityException) { stop(); status("NEEDS_PERMISSION: repair Bluetooth access") } catch(_: IllegalArgumentException) { fail("Invalid device or protocol") } catch(_: CounterExhausted) { fail("Connection counter exhausted") } }
 private val receiver=object: BroadcastReceiver() {
  override fun onReceive(c: Context,i: Intent) { h.post { safe {
   if(stopped) return@safe
   when(i.action) {
    BluetoothAdapter.ACTION_STATE_CHANGED -> if(adapter?.isEnabled==true) { if(gatt==null) connect(true) } else { close(); state("BLUETOOTH_OFF") }
    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
     @Suppress("DEPRECATION") val d=i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
     if(d?.address==address&&stage=="SECURING") when(d.bondState) {
      BluetoothDevice.BOND_BONDED->discover()
      BluetoothDevice.BOND_NONE->{ stop(); status("Pairing rejected or failed. Open setup and Start to retry.") }
     }
    }
   }
  } } }
 }
 private var registered=false
 fun start() { if(!stopped)return; stopped=false; registered=true
  val filter=IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply { addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
  if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED) else context.registerReceiver(receiver,filter)
  safe { connect(false) }; h.postDelayed(tick,1000)
 }
 private fun connect(auto: Boolean) {
  if(stopped||gatt!=null)return
  if(adapter?.isEnabled!=true) { state("BLUETOOTH_OFF"); return }
  generation++; val gen=generation
  state(if(auto) "ARMED_WAITING" else "CONNECTING")
  session=generateSequence { SecureRandom().nextInt().toLong() and 0xffffffffL }.first { it!=0L }
  gatt=adapter.getRemoteDevice(address).connectGatt(context,auto,callback,BluetoothDevice.TRANSPORT_LE)
  if(!auto) h.postDelayed({ if(!stopped&&gen==generation&&stage=="CONNECTING") fail("Connection timeout") },15000)
 }
 private fun close() {
  generation++; operations.reset(); ready=false; rx=null;tx=null; active=null;sent=null;offset=0
  publisher.clear(); parser.reset(); commandIds.clear(); session=0;id=0;lastId=0;snapshotId=0;snapshotSeq=0
  val old=gatt;gatt=null
  try { old?.disconnect() } catch(_:SecurityException) { } finally { try { old?.close() } catch(_:SecurityException) { } }
 }
 fun stop() { stopped=true; close(); h.removeCallbacks(tick); if(registered) { context.unregisterReceiver(receiver);registered=false }; state("STOPPED") }
 fun restart(reason: String) { fail(reason) }
 private fun fail(reason: String) { if(stopped)return; close(); state("ERROR: $reason; waiting to reconnect")
  val gen=generation; val delay=listOf(1000L,2000L,4000L,8000L,15000L,30000L)[retry.coerceAtMost(5)];retry++
  h.postDelayed({ if(!stopped&&gen==generation) safe { connect(true) } },delay+(0..250).random())
 }
 private fun begin(name: String,call: ()->Boolean) {
  val ticket=operations.begin(generation,name,now(),if(name=="discover")10000 else 5000)
  if(!call()) { fail("$name request refused");return }
  h.postDelayed({ if(!stopped&&operations.expired(ticket,now())) fail("$name timed out") },ticket.deadline-now())
 }
 private fun complete(name: String,result: Int,body: ()->Unit) {
  if(!operations.complete(generation,name))return
  if(result!=BluetoothGatt.GATT_SUCCESS) fail("$name GATT status $result") else body()
 }

 private fun discover() { state("DISCOVERING"); begin("discover") { gatt?.discoverServices()==true } }
 private fun writeCccd() {
  val g=gatt?:return; val c=tx?:return
  val d=c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?:return fail("Missing indication CCCD")
  if(!g.setCharacteristicNotification(c,true))return fail("Local indication subscription failed")
  state("SUBSCRIBING")
  begin("cccd") {
   if(Build.VERSION.SDK_INT>=33) g.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)==BluetoothStatusCodes.SUCCESS
   else { d.value=BluetoothGattDescriptor.ENABLE_INDICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(d) }
  }
 }
 private fun hello() {
  state("HANDSHAKING"); lastPeer=now();errorAt=now();errorBase=parser.errors
  enqueue(1,{ byteArrayOf(1,1,0,3,-128,0,0,0,1,0,0,0) })
  val gen=generation;h.postDelayed({ if(gen==generation&&stage=="HANDSHAKING") fail("HELLO acknowledgement timeout") },5000)
 }
 fun enqueue(type: Int,payload: ()->ByteArray,onSent: (Long)->Unit={}) {
  if(stopped||gatt==null||stage !in listOf("HANDSHAKING","SYNCING","READY"))return
  if(!publisher.enqueue(Publisher.Pending(type,payload,onSent))) { fail("Control queue overflow");return }
  pump()
 }
 fun snapshot(payload: ()->ByteArray) {
  enqueue(16,payload) { messageId -> snapshotId=messageId
   val gen=generation
   if(!ready) h.postDelayed({ if(gen==generation&&!ready)fail("Snapshot acknowledgement timeout") },10000)
  }
 }
 private fun pump() {
  if(active!=null||pending.isNotEmpty()||stopped)return
  val p=publisher.next()?:return
  if(id==0xffffffffL)return fail("Message counter exhausted")
  val bytes=p.payload()
  if(p.type==16) snapshotSeq=Protocol.u32(bytes,8)
  val messageId=++id; active=Frame(p.type,session,messageId,bytes).encode();offset=0
  // Register expected snapshot ID before ATT can deliver the corresponding application ACK.
  if(p.type==16) snapshotId=messageId
  sent={p.sent(messageId)};writeChunk()
 }
 private fun writeChunk() {
  val bytes=active?:return;val g=gatt?:return;val c=rx?:return
  val value=bytes.copyOfRange(offset,(offset+20).coerceAtMost(bytes.size))
  begin("write") {
   if(Build.VERSION.SDK_INT>=33)g.writeCharacteristic(c,value,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS
   else { c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;c.value=value;@Suppress("DEPRECATION") g.writeCharacteristic(c) }
  }
 }
 private fun receive(f: Frame,at: Long) {
  if(f.session!=session)return fail("Wrong session")
  if(f.type!=32&&f.id<=lastId)return fail("Old message ID")
  if(f.type !in listOf(2,18,32,48,65,126,127))return fail("Wrong message direction")
  if(f.type==32) {
   if(f.id<=lastId&&!commandIds.contains(f.id))return fail("Old command ID outside cache")
   if(f.id>lastId) { commandIds.addLast(f.id);while(commandIds.size>32)commandIds.removeFirst() }
  }
  lastId=maxOf(lastId,f.id);lastPeer=at
  when(f.type) {
   2->{ if(stage!="HANDSHAKING"||f.id!=1L||f.payload[0]!=1.toByte()||f.payload[1]!=0.toByte())return fail("Protocol version or handshake rejected")
    state("SYNCING");synced() }
   18->{ if(Protocol.u32(f.payload,0)==snapshotId&&Protocol.u32(f.payload,4)==snapshotSeq) { ready=true;retry=0;state("READY") } }
   127->fail("Flipper closed")
   126->fail("Peer protocol error ${Protocol.u16(f.payload,4)}")
   else->incoming(f,at)
  }
 }
 private val callback=object: BluetoothGattCallback() {
  private fun post(g: BluetoothGatt,body: ()->Unit) { h.post { if(g===gatt&&!stopped)safe(body) } }
  override fun onConnectionStateChange(g: BluetoothGatt,s: Int,newState: Int)=post(g) {
   if(s!=BluetoothGatt.GATT_SUCCESS||newState==BluetoothProfile.STATE_DISCONNECTED)fail("Link disconnected ($s)")
   else if(newState==BluetoothProfile.STATE_CONNECTED) {
    if(g.device.bondState==BluetoothDevice.BOND_BONDED)discover() else { state("SECURING");status("SECURING: confirm the matching code on both devices")
     if(g.device.bondState!=BluetoothDevice.BOND_BONDING&&!g.device.createBond())fail("Pairing could not start") }
   }
  }
  override fun onServicesDiscovered(g: BluetoothGatt,s: Int)=post(g) { complete("discover",s) {
   val service=g.getService(UUID.fromString(Protocol.SERVICE));rx=service?.getCharacteristic(UUID.fromString(Protocol.RX));tx=service?.getCharacteristic(UUID.fromString(Protocol.TX))
   if(rx==null||tx==null||rx!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE==0||tx!!.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE==0)fail("Not a compatible Now Playing service") else writeCccd()
  } }
  override fun onDescriptorWrite(g: BluetoothGatt,d: BluetoothGattDescriptor,s: Int)=post(g) { if(d.characteristic.uuid==tx?.uuid)complete("cccd",s) { hello() } }
  override fun onCharacteristicWrite(g: BluetoothGatt,c: BluetoothGattCharacteristic,s: Int)=post(g) { if(c.uuid==rx?.uuid)complete("write",s) {
   offset+=20
   if(offset>=(active?.size?:0)) { active=null;val done=sent;sent=null;done?.invoke();pump() } else writeChunk()
  } }
  private fun data(g: BluetoothGatt,c: BluetoothGattCharacteristic,bytes: ByteArray) { val copy=bytes.copyOf();val at=now();post(g) {
   if(c.uuid!=tx?.uuid)return@post
   if(copy.size>128)return@post fail("Oversized characteristic value")
   parser.feed(copy,at) { receive(it,at) }
  } }
  override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray)=data(g,c,value)
  @Deprecated("Legacy callback for API 26–32")
  override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic) { if(Build.VERSION.SDK_INT<33) { @Suppress("DEPRECATION") val bytes=c.value;data(g,c,bytes?:byteArrayOf()) } }
 }
 private val tick=object: Runnable { override fun run() { if(stopped)return;safe {
  val n=now()
  if(parser.expire(n))fail("Partial frame timeout")
  if(n-errorAt>=10000) { errorAt=n;errorBase=parser.errors }
  if(parser.errors-errorBase>=3)fail("Repeated framing errors")
  if(stage in listOf("READY","SYNCING")&&n-lastPeer>=35000)fail("Peer stale")
 };if(!stopped)h.postDelayed(this,1000) } }
}
