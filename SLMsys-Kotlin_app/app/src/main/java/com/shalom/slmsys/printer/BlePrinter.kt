package com.shalom.slmsys.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class BlePrinter(private val context: Context) {

    companion object {
        private const val TAG = "BlePrinter"
        private val SERVICE_UUIDS = listOf(
            "000018f0-0000-1000-8000-00805f9b34fb",
            "49535343-fe7d-4ae5-8fa9-9fafd205e455",
            "e7810a71-73ae-499d-8c15-faa9aef0c3f2",
            "0000ffe0-0000-1000-8000-00805f9b34fb",
            "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
            "0000ae30-0000-1000-8000-00805f9b34fb",
            "0000ff00-0000-1000-8000-00805f9b34fb",
            "0000fee7-0000-1000-8000-00805f9b34fb"
        ).map { UUID.fromString(it) }

        // chunk size vem do MTU negociado em tempo de execução
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var writeReady: CompletableDeferred<Boolean>? = null
    @Volatile private var negotiatedMtu: Int = 23
    private val writeMutex = Mutex()

    @Volatile var deviceName: String = ""
        private set
    @Volatile var deviceAddress: String = ""
        private set

    val isConnected: Boolean
        get() = gatt != null && writeChar != null

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.Main) {
        disconnect()
        deviceName = device.name ?: device.address ?: "Impressora"
        deviceAddress = device.address ?: ""

        val deferred = CompletableDeferred<Result<Unit>>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "connected status=$status")
                    try {
                        g.requestMtu(512)
                    } catch (_: Exception) {
                        g.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!deferred.isCompleted) {
                        deferred.complete(Result.failure(Exception("Desconectado (status=$status)")))
                    }
                    cleanup()
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "mtu=$mtu status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
                try {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: Exception) { }
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    deferred.complete(Result.failure(Exception("Falha ao descobrir serviços ($status)")))
                    return
                }
                val found = findWriteCharacteristic(g)
                if (found == null) {
                    deferred.complete(Result.failure(Exception("Nenhuma característica gravável na impressora")))
                    g.disconnect()
                    return
                }
                writeChar = found
                gatt = g
                try {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: Exception) { }
                Log.d(TAG, "write char ${found.uuid} props=${found.properties} mtu=$negotiatedMtu")
                deferred.complete(Result.success(Unit))
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writeReady?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        try {
            val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
            gatt = g
            // timeout 15s
            val result = kotlinx.coroutines.withTimeoutOrNull(15_000) { deferred.await() }
                ?: Result.failure(Exception("Timeout ao conectar (15s)"))
            result
        } catch (e: Exception) {
            cleanup()
            Result.failure(e)
        }
    }

    private fun findWriteCharacteristic(g: BluetoothGatt): BluetoothGattCharacteristic? {
        for (uuid in SERVICE_UUIDS) {
            val service = g.getService(uuid) ?: continue
            pickWrite(service.characteristics)?.let { return it }
        }
        for (service in g.services) {
            pickWrite(service.characteristics)?.let { return it }
        }
        return null
    }

    private fun pickWrite(chars: List<BluetoothGattCharacteristic>): BluetoothGattCharacteristic? {
        // Prefer WRITE_NO_RESPONSE then WRITE
        val noResp = chars.firstOrNull {
            (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        }
        if (noResp != null) return noResp
        return chars.firstOrNull {
            (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        }
    }

    /**
     * Envia bytes para a térmica.
     *
     * Importante: WRITE_NO_RESPONSE + delay muito baixo estoura o buffer da
     * impressora e a etiqueta sai **pela metade**. Aqui o equilíbrio é:
     * - chunks moderados (não o MTU inteiro)
     * - pausa proporcional à velocidade (1=segura … 8=rápida)
     * - pausa extra a cada N chunks para o buffer esvaziar
     * - pausa final conforme o tamanho do job
     */
    @SuppressLint("MissingPermission")
    suspend fun write(bytes: ByteArray, speed: Int = 5): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val g = gatt ?: return@withContext Result.failure(Exception("Não conectado"))
            val char = writeChar ?: return@withContext Result.failure(Exception("Sem característica de escrita"))
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("Nada para imprimir"))

            val spd = speed.coerceIn(1, 8)

            // Chunks: MTU ajuda, mas muitas térmicas corrompem acima de ~100–120
            val mtuPayload = (negotiatedMtu - 3).coerceIn(20, 180)
            val chunkSize = when {
                spd <= 2 -> minOf(mtuPayload, 48)
                spd <= 4 -> minOf(mtuPayload, 72)
                spd <= 6 -> minOf(mtuPayload, 96)
                else -> minOf(mtuPayload, 120)
            }

            // Pausa entre chunks (ms). Velocidade 5 ≈ 22ms — bem mais rápida
            // que o antigo 70–120ms, sem dropar metade da etiqueta.
            val chunkDelay = when (spd) {
                1 -> 55L
                2 -> 42L
                3 -> 32L
                4 -> 26L
                5 -> 20L
                6 -> 15L
                7 -> 11L
                else -> 8L
            }

            val useNoResponse =
                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            val writeType = if (useNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            Log.d(
                TAG,
                "writing ${bytes.size}b speed=$spd delay=${chunkDelay}ms chunk=$chunkSize " +
                    "noResp=$useNoResponse mtu=$negotiatedMtu"
            )

            try {
                var offset = 0
                var chunkIndex = 0
                var consecutiveFails = 0
                while (offset < bytes.size) {
                    val end = (offset + chunkSize).coerceAtMost(bytes.size)
                    val slice = bytes.copyOfRange(offset, end)

                    if (!useNoResponse) {
                        writeReady = CompletableDeferred()
                    }

                    val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeCharacteristic(char, slice, writeType) == 0
                    } else {
                        @Suppress("DEPRECATION")
                        run {
                            char.writeType = writeType
                            char.value = slice
                            g.writeCharacteristic(char)
                        }
                    }

                    if (!ok) {
                        consecutiveFails++
                        if (consecutiveFails > 8) {
                            return@withContext Result.failure(
                                Exception("Falha no envio (chunk $chunkIndex). Tente velocidade menor.")
                            )
                        }
                        delay(40)
                        continue
                    }
                    consecutiveFails = 0

                    if (!useNoResponse) {
                        val ack = kotlinx.coroutines.withTimeoutOrNull(2000) { writeReady?.await() }
                        if (ack != true) {
                            Log.w(TAG, "ack timeout chunk $chunkIndex")
                            delay(chunkDelay)
                        }
                    } else {
                        // NO_RESPONSE: precisa de pausa senão o buffer da térmica estoura
                        delay(chunkDelay)
                        // A cada 6 chunks, pausa extra para esvaziar o buffer
                        if (chunkIndex > 0 && chunkIndex % 6 == 0) {
                            delay(chunkDelay * 2)
                        }
                    }

                    offset = end
                    chunkIndex++
                }

                // Tempo para a impressora terminar de processar (proporcional ao tamanho)
                val tailMs = (80L + bytes.size / 80L).coerceIn(80L, 400L)
                delay(tailMs)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        cleanup()
    }

    private fun cleanup() {
        gatt = null
        writeChar = null
        deviceName = ""
        deviceAddress = ""
        writeReady = null
        negotiatedMtu = 23
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
