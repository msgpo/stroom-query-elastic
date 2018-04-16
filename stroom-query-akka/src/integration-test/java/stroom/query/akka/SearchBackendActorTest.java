package stroom.query.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.audit.security.ServiceUser;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SearchBackendActorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchBackendActorTest.class);

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testSearchActorValid() {
        // Given
        final ServiceUser user = new ServiceUser.Builder()
                .name("Me")
                .jwt(UUID.randomUUID().toString())
                .build();
        final String type1 = "typeOne";
        final TestQueryService queryService = new TestQueryService(new SearchResponse.FlatResultBuilder().build());
        final TestKit testProbe = new TestKit(system);
        final ActorRef searchActor = system.actorOf(SearchBackendActor.props(true, type1, user, queryService, testProbe.getRef()));

        // When
        searchActor.tell(new SearchMessages.SearchJob(type1, new SearchRequest.Builder().build()), ActorRef.noSender());

        // Then
        final SearchMessages.SearchJobComplete jobComplete = testProbe.expectMsgClass(SearchMessages.SearchJobComplete.class);
        assertNotNull(jobComplete.getResponse());
        assertNull(jobComplete.getError());
    }

    @Test
    public void testSearchActorEmpty() {
        // Given
        final ServiceUser user = new ServiceUser.Builder()
                .name("Me")
                .jwt(UUID.randomUUID().toString())
                .build();
        final String type1 = "typeOne";
        final TestQueryService queryService = new TestQueryService();
        final TestKit testProbe = new TestKit(system);
        final ActorRef searchActor = system.actorOf(SearchBackendActor.props(true, type1, user, queryService, testProbe.getRef()));

        // When
        searchActor.tell(new SearchMessages.SearchJob(type1, new SearchRequest.Builder().build()), ActorRef.noSender());

        // Then
        final SearchMessages.SearchJobComplete jobComplete = testProbe.expectMsgClass(SearchMessages.SearchJobComplete.class);
        assertNull(jobComplete.getResponse());
        assertNotNull(jobComplete.getError());

        LOGGER.info("Error Seen correctly {}", jobComplete.getError());
    }

    @Test
    public void testSearchFilteredByType() {
        // Given
        final ServiceUser user = new ServiceUser.Builder()
                .name("Me")
                .jwt(UUID.randomUUID().toString())
                .build();
        final String type1 = "typeOne";
        final String type2 = "typeTwo";
        final TestQueryService queryService1 = new TestQueryService();
        final TestKit testProbe = new TestKit(system);
        final ActorRef searchActor1 = system.actorOf(SearchBackendActor.props(true, type1, user, queryService1, testProbe.getRef()));

        // When
        searchActor1.tell(new SearchMessages.SearchJob(type2, new SearchRequest.Builder().build()), ActorRef.noSender());

        // Then
        testProbe.expectNoMsg();
    }

    @Test
    public void testMultiple() {
        // Given
        final ServiceUser user = new ServiceUser.Builder()
                .name("Me")
                .jwt(UUID.randomUUID().toString())
                .build();
        final String type1 = "typeOne";
        final TestQueryService queryService = new TestQueryService(new SearchResponse.FlatResultBuilder().build());
        final TestKit testProbe = new TestKit(system);
        final ActorRef searchActor = system.actorOf(SearchBackendActor.props(true, type1, user, queryService, testProbe.getRef()));
        final int numberOfJobs = 3;

        final long startTime = System.currentTimeMillis();

        // When
        IntStream.range(0, numberOfJobs).forEach(i -> {
            searchActor.tell(new SearchMessages.SearchJob(type1, new SearchRequest.Builder()
                    .key(Integer.toString(i))
                    .build()), ActorRef.noSender());
        });

        // Then
        final List<Object> responses = testProbe.receiveN(numberOfJobs);

        responses.stream()
                .filter(i -> i instanceof SearchMessages.SearchJobComplete)
                .map(i -> (SearchMessages.SearchJobComplete) i)
                .forEach(jobComplete -> {
                    LOGGER.info("Job Complete Received {}", jobComplete);
                    assertNotNull(jobComplete.getResponse());
                    assertNull(jobComplete.getError());
                });

        final long endTime = System.currentTimeMillis();

        final Duration testTime = Duration.of(endTime - startTime, ChronoUnit.MILLIS);
        LOGGER.info("Test took {} seconds", testTime.get(ChronoUnit.SECONDS));
    }
}