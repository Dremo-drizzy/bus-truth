package io.github.dremo_drizzy.bustruth.collector.mapper;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import io.github.dremo_drizzy.bustruth.common.ServiceAlert;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns a decoded alerts snapshot into {@link ServiceAlert} records. */
@Component
public class ServiceAlertMapper {

    private static final String PREFERRED_LANGUAGE = "en";

    public List<ServiceAlert> map(FeedMessage feed) {
        Instant feedTimestamp = Instant.ofEpochSecond(feed.getHeader().getTimestamp());
        List<ServiceAlert> mapped = new ArrayList<>(feed.getEntityCount());

        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasAlert()) {
                continue;
            }
            Alert alert = entity.getAlert();
            mapped.add(new ServiceAlert(
                    entity.getId(),
                    alert.hasCause() ? alert.getCause().name() : null,
                    alert.hasEffect() ? alert.getEffect().name() : null,
                    alert.hasSeverityLevel() ? alert.getSeverityLevel().name() : null,
                    text(alert.getHeaderText()),
                    text(alert.getDescriptionText()),
                    text(alert.getCauseDetail()),
                    text(alert.getEffectDetail()),
                    activePeriods(alert),
                    informedEntities(alert),
                    feedTimestamp));
        }
        return mapped;
    }

    /**
     * Unlike trip updates, the entity id is used here: an alert has no other
     * identity, and nothing downstream needs alerts ordered per key.
     */
    private static List<ServiceAlert.ActivePeriod> activePeriods(Alert alert) {
        List<ServiceAlert.ActivePeriod> periods = new ArrayList<>(alert.getActivePeriodCount());
        for (GtfsRealtime.TimeRange range : alert.getActivePeriodList()) {
            periods.add(new ServiceAlert.ActivePeriod(
                    range.hasStart() ? Instant.ofEpochSecond(range.getStart()) : null,
                    range.hasEnd() ? Instant.ofEpochSecond(range.getEnd()) : null));
        }
        return periods;
    }

    private static List<ServiceAlert.InformedEntity> informedEntities(Alert alert) {
        List<ServiceAlert.InformedEntity> entities = new ArrayList<>(alert.getInformedEntityCount());
        for (GtfsRealtime.EntitySelector selector : alert.getInformedEntityList()) {
            entities.add(new ServiceAlert.InformedEntity(
                    TripUpdateMapper.emptyToNull(selector.getAgencyId()),
                    TripUpdateMapper.emptyToNull(selector.getRouteId()),
                    TripUpdateMapper.emptyToNull(selector.getStopId()),
                    TripUpdateMapper.emptyToNull(selector.getTrip().getTripId())));
        }
        return entities;
    }

    /**
     * Picks one string out of the feed's translations: English if present, otherwise
     * whichever came first. Halifax publishes English only, so modelling a language
     * map would add a structure nothing reads — but falling back rather than
     * assuming "en" means a future French translation still produces text.
     */
    static String text(TranslatedString translated) {
        if (translated.getTranslationCount() == 0) {
            return null;
        }
        for (TranslatedString.Translation translation : translated.getTranslationList()) {
            if (PREFERRED_LANGUAGE.equalsIgnoreCase(translation.getLanguage())) {
                return TripUpdateMapper.emptyToNull(translation.getText());
            }
        }
        return TripUpdateMapper.emptyToNull(translated.getTranslation(0).getText());
    }
}
