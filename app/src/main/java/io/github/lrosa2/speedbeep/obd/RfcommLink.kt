package io.github.lrosa2.speedbeep.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.ObdException
import io.github.lrosa2.speedbeep.engine.ObdLink
import java.io.IOException
import java.util.UUID

/** Classic Bluetooth serial (SPP) link to an ELM327 adapter paired in Android's settings (spec §4.5). */
class RfcommLink private constructor(private val socket: BluetoothSocket) : ObdLink {
    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Blocks while connecting (up to about 12 s). Throws [ObdException]. */
        @SuppressLint("MissingPermission") // checked first: without "Nearby devices" Bluetooth calls throw
        fun open(context: Context, address: String): RfcommLink {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                throw ObdException("\"Nearby devices\" is not allowed")
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw ObdException("this phone has no Bluetooth")
            if (!adapter.isEnabled) throw ObdException("Bluetooth is off")
            val device = adapter.getRemoteDevice(address)
            return try {
                RfcommLink(connect(device.createRfcommSocketToServiceRecord(SPP)))
            } catch (first: IOException) {
                // Some cheap copies only accept an unauthenticated connection.
                try {
                    RfcommLink(connect(device.createInsecureRfcommSocketToServiceRecord(SPP)))
                } catch (second: IOException) {
                    throw ObdException("cannot connect to the adapter (${second.message ?: first.message})")
                }
            }
        }

        private fun connect(socket: BluetoothSocket): BluetoothSocket = try {
            socket.connect()
            socket
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
    }

    private val input = socket.inputStream
    private val output = socket.outputStream

    override fun send(command: String) {
        try {
            while (input.available() > 0) input.read() // drop what is left of an earlier reply
            output.write("$command\r".toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (e: IOException) {
            throw ObdException("connection lost (${e.message})")
        }
    }

    override fun readReply(timeoutMs: Long): String {
        val reply = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        try {
            while (true) {
                while (input.available() > 0) {
                    val b = input.read()
                    if (b < 0) throw ObdException("connection closed")
                    if (b == '>'.code) return reply.toString()
                    if (b != 0) reply.append(b.toChar())
                }
                if (SystemClock.elapsedRealtime() > deadline) {
                    throw ObdException("no reply within $timeoutMs ms (got \"${reply.toString().trim()}\")")
                }
                Thread.sleep(10)
            }
        } catch (e: IOException) {
            throw ObdException("connection lost (${e.message})")
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
