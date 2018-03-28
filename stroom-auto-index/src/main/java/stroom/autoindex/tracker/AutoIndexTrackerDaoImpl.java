package stroom.autoindex.tracker;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;

import javax.inject.Inject;
import java.util.Optional;

public class AutoIndexTrackerDaoImpl implements AutoIndexTrackerDao {
    private final DSLContext database;

    private static final String DOC_REF_UUID = "docRefUuid";
    private static final String FROM = "fromValue";
    private static final String TO = "toValue";

    private static final Table<Record> WINDOW_TABLE = DSL.table(AutoIndexTracker.TABLE_NAME);
    public static final Field<String> FIELD_DOC_REF_UUID = DSL.field(DOC_REF_UUID, String.class);
    public static final Field<ULong> FIELD_FROM = DSL.field(FROM, ULong.class);
    public static final Field<ULong> FIELD_TO = DSL.field(TO, ULong.class);

    private static final WindowMerger<Long, TrackerWindow> longTimeMerger =
            WindowMerger.<Long, TrackerWindow>withValueGenerator((from, to) -> TrackerWindow.from(from).to(to))
                    .comparator(Long::compareTo)
                    .build();

    @Inject
    public AutoIndexTrackerDaoImpl(final DSLContext database) {
        this.database = database;
    }

    @Override
    public AutoIndexTracker get(final String docRefUuid) {
        return database.transactionResult(c -> getInTransaction(c, docRefUuid));
    }

    @Override
    public AutoIndexTracker addWindow(final String docRefUuid,
                                      final TrackerWindow window) {
        return database.transactionResult(c -> {
            final AutoIndexTracker current = getInTransaction(c, docRefUuid);

            // Attempt to merge this new window with any existing ones that can be replaced
            final Optional<TrackerWindow> windowToAdd = longTimeMerger.merge(window)
                    .with(current.getWindows())
                    .deleteWith(tw -> DSL.using(c)
                            .deleteFrom(WINDOW_TABLE)
                            .where(FIELD_DOC_REF_UUID.equal(docRefUuid)
                                    .and(FIELD_FROM.equal(ULong.valueOf(tw.getFrom()))))
                            .execute())
                    .execute();

            // If there is still a window to add, add it to the database
            windowToAdd.ifPresent(tw ->
                    DSL.using(c).insertInto(WINDOW_TABLE)
                            .columns(FIELD_DOC_REF_UUID, FIELD_FROM, FIELD_TO)
                            .values(docRefUuid, ULong.valueOf(tw.getFrom()), ULong.valueOf(tw.getTo()))
                            .execute()
            );

            return getInTransaction(c, docRefUuid);
        });
    }

    @Override
    public AutoIndexTracker clearWindows(final String docRefUuid) {
        return database.transactionResult(c -> {
            DSL.using(c).deleteFrom(WINDOW_TABLE)
                    .where(FIELD_DOC_REF_UUID.equal(docRefUuid))
                    .execute();

            return getInTransaction(c, docRefUuid);
        });
    }

    private AutoIndexTracker getInTransaction(final Configuration c,
                                              final String docRefUuid) {
        return DSL.using(c)
                .select()
                .from(WINDOW_TABLE)
                .where(FIELD_DOC_REF_UUID.equal(docRefUuid))
                .orderBy(FIELD_FROM)
                .fetch(AutoIndexTrackerDaoImpl::fromRecord)
                .stream()
                .filter(TrackerWindow::isBound)
                .reduce(AutoIndexTracker.forDocRef(docRefUuid),
                        AutoIndexTracker::withWindow,
                        (a, b) -> a.withWindows(b.getWindows()));
    }

    /**
     * JOOQ {@link RecordMapper} for {@link TrackerWindow}
     * @param r The jOOQ Record to map
     * @return The Tracker Window created from those details
     */
    public static TrackerWindow fromRecord(final Record r) {
        return TrackerWindow
                .from(r.get(FIELD_FROM).longValue())
                .to(r.get(FIELD_TO).longValue());
    }
}
