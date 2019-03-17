package berlin.intero.sentientlighthub.wobble.scheduled

import berlin.intero.sentientlighthub.common.SentientProperties
import berlin.intero.sentientlighthub.common.model.MQTTEvent
import berlin.intero.sentientlighthub.common.model.payload.SingleLEDPayload
import berlin.intero.sentientlighthub.common.services.ConfigurationService
import berlin.intero.sentientlighthub.common.tasks.MQTTPublishAsyncTask
import com.google.gson.Gson
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.logging.Logger

/**
 * This scheduled task
 * <li> calculates which values should be used according to the wobble pattern
 * <li> calls {@link MQTTPublishAsyncTask} to publish the characteristics' values to a MQTT broker
 */
@Component
class WobbleScheduledTask {
    companion object {
        private val log: Logger = Logger.getLogger(WobbleScheduledTask::class.simpleName)

        // Pattern configuration
        private const val WOBBLE_SEND_RATE = 3L
        private const val MIN_VALUE = 0
        private const val MAX_VALUE = 60
    }

    @Scheduled(fixedRate = WobbleScheduledTask.WOBBLE_SEND_RATE)
    fun calculateValue() {

        val currentMillis = System.currentTimeMillis()
        val waveLength = (MAX_VALUE - MIN_VALUE) * WOBBLE_SEND_RATE
        val t = currentMillis % waveLength

        // Define slopes
        val rangeUpSlope = LongRange(0, waveLength / 2)
        val rangeDownSlope = LongRange(waveLength / 2, waveLength)

        // Calculate value depending on time
        val value = when (t) {
            in rangeUpSlope -> (t * ((MAX_VALUE.toDouble() - MIN_VALUE.toDouble()) / (waveLength / 2)) + MIN_VALUE).toInt()
            in rangeDownSlope -> (t * ((MIN_VALUE.toDouble() - MAX_VALUE.toDouble()) / (waveLength / 2)) + (2 * MAX_VALUE - MIN_VALUE)).toInt()
            else -> -1
        }

        // Assemble values to be published
        val mqttEvents = ArrayList<MQTTEvent>()

        ConfigurationService.actorConfig?.actorDevices?.forEach { intendedDevice ->
            intendedDevice.strips.forEach { strip ->
                strip.leds.forEach { led ->

                    val stripId = strip.index
                    val ledId = led.index

                    val topic = "${SentientProperties.MQTT.Topic.LED}/${led.index}"
                    val payload = SingleLEDPayload(stripId, ledId, value.toString(), value.toString(), value.toString())

                    val mqttEvent = MQTTEvent(topic, Gson().toJson(payload), Date())
                    mqttEvents.add(mqttEvent)
                }
            }
        }

        // Publish values
        if (mqttEvents.isNotEmpty()) {
            // Call MQTTPublishAsyncTask
            SyncTaskExecutor().execute(MQTTPublishAsyncTask(mqttEvents))
        } else {
            log.info(".")
            Thread.sleep(SentientProperties.Frequency.UNSUCCESSFUL_TASK_DELAY)
        }
    }
}
