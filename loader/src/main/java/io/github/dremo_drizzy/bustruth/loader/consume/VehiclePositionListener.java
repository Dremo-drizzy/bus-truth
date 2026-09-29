package io.github.dremo_drizzy.bustruth.loader.consume;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.github.dremo_drizzy.bustruth.loader.store.VehiclePositionWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

/**
 * Consumes hfx.vehicle-positions and hands each message to the writer.
 *
 * <p>Deliberately holds no logic beyond the unreadable-message check: what makes an
 * insert idempotent belongs in SQL, and what a vehicle position means belongs to the
 * processor.
 *
 * <p>Offsets are committed by the container after this method returns (ack-mode
 * record), so the offset only advances once the row is committed. A crash in between
 * redelivers the message, which the writer's ON CONFLICT absorbs.
 */
@Component
public class VehiclePositionListener {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionListener.class);
    private static final LogAccessor LOG_ACCESSOR = new LogAccessor(VehiclePositionListener.class);

    private final VehiclePositionWriter writer;
    private final Counter skipped;

    public VehiclePositionListener(VehiclePositionWriter writer, MeterRegistry meterRegistry) {
        this.writer = writer;
        this.skipped = Counter.builder("loader.messages.skipped")
                .description("Messages that could not be deserialized and were not stored")
                .tag("topic", "hfx.vehicle-positions")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "hfx.vehicle-positions")
    public void onMessage(ConsumerRecord<String, VehiclePosition> record) {
        VehiclePosition position = record.value();

        if (position == null) {
            // ErrorHandlingDeserializer delivers a null payload and puts the cause in
            // a header, rather than throwing before the listener is reached — which
            // would make the container retry this offset forever.
            skipped.increment();
            // The header constant is deprecated for removal, but the type that
            // replaces it is package-private, so this is still the only public way
            // to read the cause. Revisit when spring-kafka exposes the successor.
            @SuppressWarnings("removal")
            DeserializationException cause = SerializationUtils.getExceptionFromHeader(
                    record, SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, LOG_ACCESSOR);
            // Coordinates, so the message can be found and replayed by hand. A skip
            // nobody can locate is data loss with a log line attached.
            log.warn("skipping unreadable message at {}-{} offset {}: {}",
                    record.topic(), record.partition(), record.offset(),
                    cause == null ? "no exception header" : cause.getMessage());
            return;
        }

        boolean written = writer.insert(position, record.partition(), record.offset());
        if (log.isDebugEnabled()) {
            log.debug("{} {} at {}-{} offset {}",
                    written ? "stored" : "already stored", position.vehicleId(),
                    record.topic(), record.partition(), record.offset());
        }
    }
}
