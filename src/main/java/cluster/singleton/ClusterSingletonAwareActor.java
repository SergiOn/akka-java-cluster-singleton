package cluster.singleton;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

class ClusterSingletonAwareActor extends AbstractLoggingActor {
    private final ActorRef clusterSingletonProxy;
    private final FiniteDuration tickInterval = Duration.create(5, TimeUnit.SECONDS);
    private Cancellable ticker;
    private int pingId;
    private int pongId;

    private ClusterSingletonAwareActor(ActorRef clusterSingletonProxy) {
        this.clusterSingletonProxy = clusterSingletonProxy;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("tick", t -> tick())
                .match(ClusterSingletonMessages.Pong.class, this::pong)
                .build();
    }

    private void tick() {
        ++pingId;
        log().debug("Ping({}) -> {}", pingId, clusterSingletonProxy);
        clusterSingletonProxy.tell(new ClusterSingletonMessages.Ping(pingId), self());
    }

    private void pong(ClusterSingletonMessages.Pong pong) {
        log().debug("Pong({}) <- {}", pong.id, sender());
        if (++pongId != pong.id) {
            log().warning("Pong id invalid, expected {}, actual {}", pongId, pong.id);
        }
        pongId = pong.id;
    }

    @Override
    public void preStart() {
        log().debug("Start");
        ticker = context().system().scheduler()
                .schedule(Duration.Zero(),
                        tickInterval,
                        self(),
                        "tick",
                        context().system().dispatcher(),
                        null);
    }

    @Override
    public void postStop() {
        ticker.cancel();
        log().debug("Stop");
    }

    static Props props(ActorRef clusterSingletonProxy) {
        return Props.create(ClusterSingletonAwareActor.class, clusterSingletonProxy);
    }
}
