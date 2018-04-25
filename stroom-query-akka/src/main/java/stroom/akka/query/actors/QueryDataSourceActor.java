package stroom.akka.query.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.akka.query.messages.QueryDataSourceMessages;
import stroom.query.api.v2.DocRef;
import stroom.query.audit.service.QueryApiException;
import stroom.query.audit.service.QueryService;
import stroom.query.audit.service.QueryServiceSupplier;
import stroom.security.ServiceUser;

import java.util.concurrent.CompletableFuture;

import static akka.pattern.PatternsCS.pipe;

public class QueryDataSourceActor extends AbstractActor {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDataSourceActor.class);

    public static Props props(final QueryServiceSupplier serviceSupplier) {
        return Props.create(QueryDataSourceActor.class,
                () -> new QueryDataSourceActor(serviceSupplier));
    }

    private final QueryServiceSupplier serviceSupplier;

    public QueryDataSourceActor(final QueryServiceSupplier serviceSupplier) {
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(QueryDataSourceMessages.Job.class, this::handleDataSource)
                .build();
    }

    private void handleDataSource(final QueryDataSourceMessages.Job job) {
        final ServiceUser user = job.getUser();
        final DocRef docRef = job.getDocRef();

        LOGGER.debug("Fetching Data Source for {}", docRef);

        final CompletableFuture<QueryDataSourceMessages.JobComplete> result =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        final QueryService queryService = this.serviceSupplier.apply(docRef.getType())
                                .orElseThrow(() -> new RuntimeException("Could not find query service for " + docRef.getType()));

                        return queryService.getDataSource(user, docRef)
                                .orElseThrow(() -> new QueryApiException("Could not get response"));
                    } catch (QueryApiException e) {
                        LOGGER.error("Failed to run search", e);
                        throw new RuntimeException(e);
                    } catch (RuntimeException e) {
                        LOGGER.error("Failed to run search", e);
                        throw e;
                    }
                })
                        .thenApply(d -> QueryDataSourceMessages.complete(job, d))
                        .exceptionally(e -> QueryDataSourceMessages.failed(job, e));

        pipe(result, getContext().dispatcher()).to(getSender());
    }
}