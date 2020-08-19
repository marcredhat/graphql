package net.quarkify;
import java.util.List;
import java.util.Map.Entry;
import com.dynatrace.oneagent.sdk.api.infos.WebApplicationInfo;
import com.dynatrace.oneagent.sdk.api.IncomingWebRequestTracer;
//import com.dynatrace.oneagent.sdk.*;
import com.dynatrace.oneagent.sdk.api.InProcessLinkTracer;
import com.dynatrace.oneagent.sdk.api.InProcessLink;
import com.dynatrace.oneagent.sdk.OneAgentSDKFactory;
import com.dynatrace.oneagent.sdk.api.OneAgentSDK;
import com.dynatrace.oneagent.sdk.api.OutgoingWebRequestTracer;
import graphql.GraphQL;
import graphql.schema.*;
import graphql.schema.idl.*;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.graphql.*;
import net.quarkify.data.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphQlConfig {
    public static final List<Team> TEAMS = new ArrayList<>() {{
        add(new Team(1L, "Programmers",
                new User(1L, "Dmytro"),
                new User(2L, "Alex")
        ));
        add(new Team(2L, "Machine Learning",
                new User(3L, "Andrew NG")
        ));
    }};

    public void init(@Observes Router router) throws Exception {
        router.route("/graphql").blockingHandler(GraphQLHandler.create(createGraphQL()));
    }

    private GraphQL createGraphQL() throws Exception {
	TypeDefinitionRegistry teamsSchema = getTeamSchema();
        
OneAgentSDK oneAgentSdk = OneAgentSDKFactory.createInstance();
InProcessLink inProcessLink = oneAgentSdk.createInProcessLink();
//Provide the returned inProcessLink to the code, that does the asynchronous execution:

//OneAgentSDK oneAgentSdk = OneAgentSDKFactory.createInstance();
InProcessLinkTracer inProcessLinkTracer = oneAgentSdk.traceInProcessLink(inProcessLink);

WebApplicationInfo wsInfo = oneAgentSdk.createWebApplicationInfo("WebShopProduction", "CheckoutService", "/graphql");
IncomingWebRequestTracer tracer = oneAgentSdk.traceIncomingWebRequest(wsInfo,"http://localhost:8080/graphql", "POST");




inProcessLinkTracer.start();
	try {
	oneAgentSdk.addCustomRequestAttribute("region", "EMEA");
oneAgentSdk.addCustomRequestAttribute("salesAmount", 2500);
} catch (Exception e) {
	inProcessLinkTracer.error(e);
	// rethrow or add your exception handling
} finally {
	inProcessLinkTracer.end();
}

	System.out.println("*************************************************************");
		System.out.println("**            Running webrequest sample                    **");
		System.out.println("*************************************************************");
		try {
			WebRequestApp app = new WebRequestApp();
			app.runFakedWebrequest();
			// app.runIncomingWebrequest();
			System.out.println("sample application stopped. sleeping a while, so OneAgent is able to send data to server ...");
			Thread.sleep(15000 * 3); // we have to wait - so OneAgent is able to send data to server
		} catch (Exception e) {
			System.err.println("webrequest sample failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query",
                        builder -> builder.dataFetcher("allTeams", new VertxDataFetcher<>(this::getAllTeams))
                ).build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(teamsSchema, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private TypeDefinitionRegistry getTeamSchema() throws Exception {
        final URL resource = this.getClass().getClassLoader().getResource("teams.graphql");
        String schema = String.join("\n", Files.readAllLines(Paths.get(resource.toURI())));
        return new SchemaParser().parse(schema);
    }

    private void getAllTeams(DataFetchingEnvironment env, Promise<List<Team>> future) {
        final String excluding = env.getArgument("excluding");
        future.complete(
                TEAMS.stream()
                        .filter(it -> !it.name.equals(excluding))
                        .collect(Collectors.toList())
        );
    }

}
