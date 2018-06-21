package cluster.singleton;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Runner {
    public static void main(String[] args) {
        List<ActorSystem> actorSystems = args.length == 0
                ? startupClusterNodes(Arrays.asList("2551", "2552", "0"))
                : startupClusterNodes(Arrays.asList(args));

        hitEnterToStop();

        actorSystems.forEach(actorSystem -> {
            Cluster cluster = Cluster.get(actorSystem);
            cluster.leave(cluster.selfAddress());
        });
    }

    private static List<ActorSystem> startupClusterNodes(List<String> ports) {
        System.out.printf("Start cluster on port(s) %s%n", ports);
        List<ActorSystem> actorSystems = new ArrayList<>();

        ports.forEach(port -> {
            ActorSystem actorSystem = ActorSystem.create("singleton", setupClusterNodeConfig(port));

            actorSystem.actorOf(ClusterListenerActor.props(), "clusterListener");
            createClusterSingletonManagerActor(actorSystem);
            actorSystem.actorOf(ClusterSingletonAwareActor.props(createClusterSingletonProxyActor(actorSystem)), "clusterSingletonAware");

            actorSystems.add(actorSystem);
        });

        return actorSystems;
    }

    private static Config setupClusterNodeConfig(String port) {
        return ConfigFactory.parseString(
                String.format("akka.remote.netty.tcp.port=%s%n", port) +
                        String.format("akka.remote.artery.canonical.port=%s%n", port))
                .withFallback(ConfigFactory.load());
    }

    private static void createClusterSingletonManagerActor(ActorSystem actorSystem) {
        Props clusterSingletonManagerProps = ClusterSingletonManager.props(
                ClusterSingletonActor.props(),
                PoisonPill.getInstance(),
                ClusterSingletonManagerSettings.create(actorSystem)
        );

        actorSystem.actorOf(clusterSingletonManagerProps, "clusterSingletonManager");
    }

    private static ActorRef createClusterSingletonProxyActor(ActorSystem actorSystem) {
        Props clusterSingletonProxyProps = ClusterSingletonProxy.props(
                "/user/clusterSingletonManager",
                ClusterSingletonProxySettings.create(actorSystem)
        );

        return actorSystem.actorOf(clusterSingletonProxyProps, "clusterSingletonProxy");
    }

    private static void hitEnterToStop() {
        System.out.println("Hit Enter to stop");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
