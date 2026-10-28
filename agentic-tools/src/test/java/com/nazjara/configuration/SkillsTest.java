package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.service.TravelAssistant;
import com.nazjara.tool.InsuranceTools;
import com.nazjara.tool.TravelTools;
import java.io.IOException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.skills.Skill;
import org.junit.jupiter.api.Test;

/**
 * What the skills assistant sends before and after a skill is activated, against a scripted stub
 * model and without an API key. The assistant and the skills are the beans from
 * {@link AiServicesConfig} and {@link SkillsConfig}.
 */
class SkillsTest {

    private static final String QUESTION = "I'm 34 and going to Portugal for 10 days. Which travel insurance should I get?";
    private static final InvocationParameters ALICE = InvocationParameters.from(TravelTools.USER_ID, "alice");

    private final AiServicesConfig config = new AiServicesConfig(
            new AgenticToolsProperties(10, new AgenticToolsProperties.Plan(100, 3)));

    @Test
    void firstRequestOffersDirectToolsAndSkillToolsButNoInsuranceTools() throws IOException {
        var model = new ScriptedChatModel(AiMessage.from("stub answer"));

        assistant(model).ask(QUESTION, ALICE);

        var systemMessage = (SystemMessage) model.requests.getFirst().messages().getFirst();
        System.out.println("skills, request 1 -> " + model.toolNames(0));
        System.out.println("system message -> " + systemMessage.text());
        assertThat(model.toolNames(0)).hasSize(15 + 2)
                .contains("searchFlights", "getWeatherForecast", "activate_skill", "read_skill_resource")
                .doesNotContain("quoteInsurance", "buyInsurance");
        assertThat(systemMessage.text())
                .startsWith("You are a travel assistant.")
                .contains("<name>travel-insurance</name>", "<name>packing-list</name>", "activate_skill");
    }

    @Test
    void activatingTravelInsuranceUnlocksItsToolsOnTheNextRoundTrip() throws IOException {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "activate_skill", "{\"skill_name\":\"travel-insurance\"}")),
                AiMessage.from(toolCall("call-2", "read_skill_resource", "{\"skill_name\":\"travel-insurance\",\"relative_path\":\"coverage.md\"}")),
                AiMessage.from(toolCall("call-3", "quoteInsurance", "{\"destination\":\"Portugal\",\"days\":10,\"travellerAge\":34}")),
                AiMessage.from("STANDARD fits your trip."));

        var result = assistant(model).ask(QUESTION, ALICE);

        System.out.println("skills, request 2 -> " + model.toolNames(1));
        assertThat(model.toolNames(1)).hasSize(15 + 2 + 2).contains("quoteInsurance", "buyInsurance");
        assertThat(lastToolResult(model, 1)).contains("Read `coverage.md`").doesNotContain("name: travel-insurance");
        assertThat(lastToolResult(model, 2)).contains("| PREMIUM | 1,000,000 EUR");
        assertThat(lastToolResult(model, 3)).contains("Q-BASIC-", "Q-STANDARD-", "Q-PREMIUM-");
        assertThat(result.toolExecutions().getFirst().resultObject())
                .isInstanceOfSatisfying(Skill.class, skill -> assertThat(skill.name()).isEqualTo("travel-insurance"));
        assertThat(result.intermediateResponses().size() + 1).isEqualTo(model.requests.size()).isEqualTo(4);
    }

    @Test
    void packingListAddsInstructionsButNoTools() throws IOException {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "activate_skill", "{\"skill_name\":\"packing-list\"}")),
                AiMessage.from("- [ ] Umbrella"));

        assistant(model).ask("What should I pack for Lisbon, 2026-11-03 to 2026-11-07?", ALICE);

        assertThat(model.toolNames(1)).isEqualTo(model.toolNames(0));
        assertThat(lastToolResult(model, 1)).contains("**Clothing**", "getLocalTransport");
    }

    @Test
    void unknownSkillIsReportedToTheModel() throws IOException {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "activate_skill", "{\"skill_name\":\"visa-application\"}")),
                AiMessage.from("I have no such skill."));

        var result = assistant(model).ask(QUESTION, ALICE);

        assertThat(result.toolExecutions()).singleElement().satisfies(execution -> {
            assertThat(execution.hasFailed()).isTrue();
            assertThat(execution.result()).contains("'visa-application'", "'travel-insurance'", "'packing-list'");
        });
        assertThat(result.content()).isEqualTo("I have no such skill.");
    }

    @Test
    void insuranceToolBeforeActivationIsNotAvailable() throws IOException {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "quoteInsurance", "{\"destination\":\"Portugal\",\"days\":10,\"travellerAge\":34}")),
                AiMessage.from("Let me activate the skill first."));

        var result = assistant(model).ask(QUESTION, ALICE);

        assertThat(lastToolResult(model, 1)).contains("Tool 'quoteInsurance' is not available");
        assertThat(model.toolNames(1)).doesNotContain("quoteInsurance");
        assertThat(result.content()).isEqualTo("Let me activate the skill first.");
    }

    @Test
    void buyInsuranceReceivesTheUserIdButTheModelDoesNot() throws IOException {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "activate_skill", "{\"skill_name\":\"travel-insurance\"}")),
                AiMessage.from(toolCall("call-2", "buyInsurance", "{\"quoteId\":\"Q-STANDARD-42\"}")),
                AiMessage.from("Bought."));

        var result = assistant(model).ask("Buy the STANDARD tier, quote Q-STANDARD-42.", ALICE);

        var buyInsurance = model.requests.get(1).toolSpecifications().stream()
                .filter(specification -> specification.name().equals("buyInsurance"))
                .findFirst()
                .orElseThrow();
        assertThat(buyInsurance.parameters().properties()).containsOnlyKeys("quoteId");
        assertThat(result.toolExecutions().getLast().result()).contains("Q-STANDARD-42", "alice");
        assertThat(model.requests.getFirst().messages().toString()).doesNotContain("alice");
    }

    private TravelAssistant assistant(ScriptedChatModel model) throws IOException {
        return config.skillsAssistant(model, new TravelTools(), new SkillsConfig().travelSkills(new InsuranceTools()));
    }

    private static String lastToolResult(ScriptedChatModel model, int requestIndex) {
        return ((ToolExecutionResultMessage) model.requests.get(requestIndex).messages().getLast()).text();
    }

    private static ToolExecutionRequest toolCall(String id, String name, String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }
}
