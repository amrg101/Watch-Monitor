package com.amrg.watchinfo.communication

import android.content.Context
import android.util.Log
import com.amrg.watchinfo.model.HealthData
import com.amrg.watchinfo.model.IncidentData
import com.amrg.watchinfo.utils.Constants
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class DataLayerManager(
    private val context: Context,
    private val messageClient: MessageClient,
    private val dataClient: DataClient,
    private val coroutineScope: CoroutineScope,
    private val onCommandReceived: (String, ByteArray?) -> Unit
) : MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    private val TAG = "DataLayerManager"
    private val gson = Gson()

    fun startListening() {
        messageClient.addListener(this)
        dataClient.addListener(this)
        Log.d(TAG, "Started listening to Data Layer events.")
    }

    fun stopListening() {
        messageClient.removeListener(this)
        dataClient.removeListener(this)
        Log.d(TAG, "Stopped listening to Data Layer events.")
    }

    fun sendHealthData(healthData: HealthData) {
        sendData(
            Constants.WEAR_MESSAGE_PATH_HEALTH_DATA,
            gson.toJson(healthData).toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun sendIncidentData(incidentData: IncidentData) {
        sendData(
            Constants.WEAR_MESSAGE_PATH_INCIDENT_DATA,
            gson.toJson(incidentData).toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun sendData(path: String, data: ByteArray) {
        coroutineScope.launch {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    return@launch
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, data)
                        .addOnSuccessListener {
                            Log.d(
                                TAG,
                                "Data sent to ${node.displayName} via $path"
                            )
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send data to ${node.displayName} via $path.", e)
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting connected nodes or sending message.", e)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        when (messageEvent.path) {
            Constants.WEAR_MESSAGE_PATH_COMMAND -> {
                try {
                    val commandJson = String(messageEvent.data, StandardCharsets.UTF_8)
                    val commandMap =
                        gson.fromJson(commandJson, Map::class.java) as? Map<String, String>
                    commandMap?.get(Constants.COMMAND_KEY)?.let { command ->
                        onCommandReceived(command, messageEvent.data)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing command from phone", e)
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "Data changed event received.")
        dataEvents.forEach { event -> // Handle data changes if phone app sends data items
        }
        dataEvents.release()
    }
}