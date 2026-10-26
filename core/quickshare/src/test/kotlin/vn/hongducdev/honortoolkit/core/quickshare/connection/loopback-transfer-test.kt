package vn.hongducdev.honortoolkit.core.quickshare.connection

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TIMEOUT_SECONDS = 30L

private class MemorySink : FileSink {
    val data = java.io.ByteArrayOutputStream()
    var committed = false
    var aborted = false

    override fun write(buffer: ByteArray, length: Int) = data.write(buffer, 0, length)

    override fun commit() {
        committed = true
    }

    override fun abort() {
        aborted = true
    }
}

private class Receiver(private val decision: (TransferOffer, ConsentResponder) -> Unit) : InboundListener {
    val sinks = LinkedHashMap<String, MemorySink>()
    val offer = CompletableFuture<TransferOffer>()
    var text: String? = null

    override fun onOffer(offer: TransferOffer, responder: ConsentResponder) {
        this.offer.complete(offer)
        decision(offer, responder)
    }

    override fun openSink(file: OfferedFile): FileSink = MemorySink().also { sinks[file.name] = it }

    override fun onProgress(bytesReceived: Long, bytesTotal: Long) = Unit

    override fun onTextReceived(text: String) {
        this.text = text
    }
}

private class Sender : OutboundListener {
    val pin = CompletableFuture<String>()
    val accepted = CountDownLatch(1)
    var lastProgress = 0L

    override fun onPin(pin: String) {
        this.pin.complete(pin)
    }

    override fun onAccepted() = accepted.countDown()

    override fun onProgress(bytesSent: Long, bytesTotal: Long) {
        lastProgress = bytesSent
    }
}

class LoopbackTransferTest {
    private val executor = Executors.newCachedThreadPool()

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)

    private fun outgoing(name: String, data: ByteArray) =
        OutgoingFile(name, data.size.toLong(), "application/octet-stream") { ByteArrayInputStream(data) }

    private fun transfer(
        receiver: Receiver,
        sender: Sender,
        files: List<OutgoingFile>,
    ): Pair<TransferOutcome, TransferOutcome> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val inbound = executor.submit<TransferOutcome> { InboundConnection(server.accept(), receiver).run() }
            val outbound = executor.submit<TransferOutcome> {
                OutboundConnection(Socket(InetAddress.getLoopbackAddress(), server.localPort), "Samsung test", files, sender).run()
            }
            return inbound.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) to outbound.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `accepted transfer delivers every file byte for byte and both sides show the same pin`() {
        val big = randomBytes(1_300_000)
        val small = randomBytes(17)
        val empty = ByteArray(0)
        val receiver = Receiver { _, responder -> responder.accept() }
        val sender = Sender()

        val (inboundOutcome, outboundOutcome) = transfer(
            receiver,
            sender,
            listOf(outgoing("big.bin", big), outgoing("small.txt", small), outgoing("empty.dat", empty)),
        )

        assertEquals(TransferOutcome.Completed, inboundOutcome)
        assertEquals(TransferOutcome.Completed, outboundOutcome)
        assertArrayEquals(big, receiver.sinks.getValue("big.bin").data.toByteArray())
        assertArrayEquals(small, receiver.sinks.getValue("small.txt").data.toByteArray())
        assertEquals(0, receiver.sinks.getValue("empty.dat").data.size())
        assertTrue(receiver.sinks.values.all { it.committed && !it.aborted })
        assertEquals(receiver.offer.get().pin, sender.pin.get())
        assertEquals(4, sender.pin.get().length)
        assertEquals("Samsung test", receiver.offer.get().device.name)
        assertEquals((big.size + small.size).toLong(), sender.lastProgress)
    }

    @Test
    fun `rejected transfer writes nothing and tells the sender`() {
        val receiver = Receiver { _, responder -> responder.reject() }
        val (inboundOutcome, outboundOutcome) = transfer(receiver, Sender(), listOf(outgoing("a.bin", randomBytes(100))))

        assertEquals(TransferOutcome.Rejected(RejectReason.DECLINED), inboundOutcome)
        assertEquals(TransferOutcome.Rejected(RejectReason.DECLINED), outboundOutcome)
        assertTrue(receiver.sinks.isEmpty())
    }

    @Test
    fun `consent given later from another thread still works`() {
        val data = randomBytes(5_000)
        val receiver = Receiver { _, responder ->
            executor.submit {
                Thread.sleep(300)
                responder.accept()
            }
        }
        val (inboundOutcome, outboundOutcome) = transfer(receiver, Sender(), listOf(outgoing("late.bin", data)))

        assertEquals(TransferOutcome.Completed, inboundOutcome)
        assertEquals(TransferOutcome.Completed, outboundOutcome)
        assertArrayEquals(data, receiver.sinks.getValue("late.bin").data.toByteArray())
    }

    @Test
    fun `sender cancel while waiting for consent aborts the receiver without leaving files`() {
        val gate = CountDownLatch(1)
        val receiver = Receiver { _, _ -> gate.countDown() }
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val inbound = executor.submit<TransferOutcome> { InboundConnection(server.accept(), receiver).run() }
            val connection = OutboundConnection(
                Socket(InetAddress.getLoopbackAddress(), server.localPort),
                "Samsung test",
                listOf(outgoing("x.bin", randomBytes(10))),
                Sender(),
            )
            val outbound = executor.submit<TransferOutcome> { connection.run() }
            assertTrue(gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            connection.cancel()

            assertTrue(inbound.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) is TransferOutcome.Cancelled)
            assertTrue(outbound.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) is TransferOutcome.Cancelled)
            assertTrue(receiver.sinks.isEmpty())
        }
    }
}
