package com.nazjara.tool;

import com.nazjara.model.Role;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;
import org.springframework.stereotype.Component;

/**
 * Decides which {@link TravelTools} an invocation may use, from the {@link Role} the application
 * puts in {@code InvocationParameters}. This is authorization, not relevance: tool search
 * ({@code SimpleToolSearchStrategy}, {@code VectorToolSearchStrategy}) lets the model look for the
 * tool it needs, while a provider decides which tools exist for this request at all.
 *
 * <p>LangChain4j calls {@link #provideTools} once at the start of each AI Service invocation (this
 * provider is static, {@link #isDynamic()} is {@code false}) and sends the returned specifications
 * with every model call of that invocation. The tool executors of the same result are the only
 * ones the proxy can run, so a tool withheld from a role cannot be executed even if the model
 * names it; the call goes to the hallucinated-tool-name strategy instead.
 *
 * <p>The tools are the same ones {@code AiServices.tools(travelTools)} would register
 * ({@link ToolService#findTools}), filtered by name. A missing role means {@link Role#GUEST}, so
 * forgetting to pass one removes tools instead of granting them. Spring AI's counterpart is passing
 * a different tool set on each {@code ChatClient} call.
 *
 * <p>The provider enforces whatever role it is given. Where the role comes from is the application's
 * job; in this demo it is a request header, which is not secure (see {@code TravelController}).
 */
@Component
public class RoleToolProvider implements ToolProvider {

    public static final String ROLE = "role";

    private static final Set<String> MEMBER_ONLY_TOOLS = Set.of("bookFlight", "bookHotel");

    private final Map<Role, ToolProviderResult> toolsByRole;

    /**
     * Builds the tool set of every role once; the results are immutable and shared by all requests.
     *
     * @param travelTools the bean whose {@code @Tool} methods are filtered per role
     */
    public RoleToolProvider(TravelTools travelTools) {
        var allTools = ToolService.findTools(travelTools);
        toolsByRole = Arrays.stream(Role.values())
                .collect(Collectors.toUnmodifiableMap(Function.identity(), role -> ToolProviderResult.builder()
                        .addAll(allTools.stream()
                                .filter(tool -> role == Role.MEMBER || !MEMBER_ONLY_TOOLS.contains(tool.name()))
                                .toList())
                        .build()));
    }

    /**
     * Returns the tools of the request's role.
     *
     * @param request carries the {@code InvocationParameters} passed to the AI Service method
     * @return the tools for {@link Role#GUEST} when no role was passed, otherwise for that role
     */
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Role role = request.invocationParameters().get(ROLE);
        return toolsByRole.get(role == null ? Role.GUEST : role);
    }
}
