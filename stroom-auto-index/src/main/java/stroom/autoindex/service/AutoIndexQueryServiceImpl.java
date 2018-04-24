package stroom.autoindex.service;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.FiniteDuration;
import stroom.akka.query.actors.QueryDataSourceActor;
import stroom.akka.query.messages.QueryDataSourceMessages;
import stroom.akka.query.messages.QuerySearchMessages;
import stroom.autoindex.search.SearchRequestSplitter;
import stroom.autoindex.search.SearchResponseMerger;
import stroom.autoindex.search.SplitSearchRequest;
import stroom.datasource.api.v2.DataSource;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.audit.client.NotFoundException;
import stroom.query.audit.client.RemoteClientCache;
import stroom.security.ServiceUser;
import stroom.query.audit.service.DocRefService;
import stroom.query.audit.service.QueryApiException;
import stroom.query.audit.service.QueryService;
import stroom.tracking.TimelineTracker;
import stroom.tracking.TimelineTrackerService;
import stroom.tracking.TrackerWindow;
import static akka.pattern.PatternsCS.ask;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AutoIndexQueryServiceImpl implements QueryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoIndexQueryServiceImpl.class);

    private final DocRefService<AutoIndexDocRefEntity> docRefService;

    private final TimelineTrackerService trackerService;

    private final RemoteClientCache<QueryService> remoteClientCache;

    private final ActorSystem actorSystem;

    @Inject
    @SuppressWarnings("unchecked")
    public AutoIndexQueryServiceImpl(final DocRefService docRefService,
                                     final TimelineTrackerService trackerService,
                                     final RemoteClientCache<QueryService> remoteClientCache,
                                     final ActorSystem actorSystem) {
        this.docRefService = docRefService;
        this.trackerService = trackerService;
        this.remoteClientCache = remoteClientCache;
        this.actorSystem = actorSystem;
    }

    @Override
    public String getType() {
        return AutoIndexDocRefEntity.TYPE;
    }

    @Override
    public Optional<DataSource> getDataSource(final ServiceUser user,
                                              final DocRef docRef) throws QueryApiException {
        final AutoIndexDocRefEntity docRefEntity =
                docRefService.get(user, docRef.getUuid()).orElseThrow(NotFoundException::new);

        final ActorRef actorRef = actorSystem.actorOf(QueryDataSourceActor.props(remoteClientCache));

        final Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS));
        final CompletableFuture<QueryDataSourceMessages.JobComplete> jobCompleteF =
                ask(actorRef, QueryDataSourceMessages.dataSource(user, docRefEntity.getRawDocRef()), timeout)
                        .thenApply((QueryDataSourceMessages.JobComplete.class::cast))
                        .toCompletableFuture();

        try {
            final QueryDataSourceMessages.JobComplete jobComplete = jobCompleteF.get();

            if (null != jobComplete.getError()) {
                throw new QueryApiException(jobComplete.getError());
            }

            return Optional.ofNullable(jobComplete.getResponse());
        } catch (final InterruptedException | ExecutionException e) {
            throw new QueryApiException(e);
        }
    }

    @Override
    public Optional<SearchResponse> search(final ServiceUser user,
                                           final SearchRequest request) throws QueryApiException {
        // Retrieve the full Auto Index Doc Ref for the request
        final String docRefUuid = request.getQuery().getDataSource().getUuid();
        final AutoIndexDocRefEntity docRefEntity =
                docRefService.get(user, docRefUuid).orElseThrow(NotFoundException::new);

        // Retrieve the tracker for this doc ref
        final TimelineTracker tracker = trackerService.get(docRefUuid);

        final SplitSearchRequest splitSearchRequest = SearchRequestSplitter.withSearchRequest(request)
                .autoIndex(docRefEntity)
                .tracker(tracker)
                .split();

        // Create the results merger
        final SearchResponseMerger merger = SearchResponseMerger.start();

        // Work through each split search request, sending it to the appropriate client and collate the results
        for (final Map.Entry<DocRef, Map<TrackerWindow, SearchRequest>> requestEntry : splitSearchRequest.getRequests().entrySet()) {
            final QueryService client = remoteClientCache.apply(requestEntry.getKey().getType())
                    .orElseThrow(() -> new RuntimeException("Could not get HTTP Client for Query Resource"));

            // There may be several requests to send to each client, for fragmented windows.
            requestEntry.getValue().forEach((tw, partRequest) -> {
                try {
                    final Optional<SearchResponse> searchResponse = client.search(user, partRequest);

                    searchResponse.ifPresent(merger::response);
                    if (!searchResponse.isPresent()){
                        LOGGER.warn("Could not search {}", requestEntry.getKey());
                    }
                } catch (final QueryApiException e) {
                    LOGGER.warn("Could not search {}, {}", requestEntry.getKey(), e.getLocalizedMessage());
                }
            });
        }

        return merger.merge();
    }

    @Override
    public Boolean destroy(final ServiceUser user,
                           final QueryKey queryKey) throws QueryApiException {
        return Boolean.TRUE;
    }

    @Override
    public Optional<DocRef> getDocRefForQueryKey(final ServiceUser user,
                                                 final QueryKey queryKey) throws QueryApiException {
        return Optional.empty();
    }
}
