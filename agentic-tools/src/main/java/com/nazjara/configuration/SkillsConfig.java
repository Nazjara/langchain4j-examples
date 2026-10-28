package com.nazjara.configuration;

import com.nazjara.tool.InsuranceTools;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the agent skills ({@code langchain4j-skills}, {@code @Experimental}) from
 * {@code src/main/resources/skills/<name>/SKILL.md}.
 *
 * <p>{@link ClassPathSkillLoader} reads every skill directory once, at startup: the front matter's
 * {@code name} and {@code description}, the body as the skill's instructions, and every other text
 * file as a resource ({@code scripts/} is skipped, so a skill can never run code). Nothing is read
 * from disk at inference time.
 *
 * <p>A {@code SKILL.md} cannot declare tools, so tools are attached here: {@code travel-insurance}
 * is rebuilt with the {@link InsuranceTools} bean. The loader returns {@code DefaultFileSystemSkill}
 * from a directory and {@code DefaultSkill} from a jar; both have {@code toBuilder()}, but the
 * declared type is {@link Skill}, so the skill is copied through {@link Skill#builder()} instead,
 * which works for either. {@code packing-list} stays instructions-only.
 *
 * <p>{@link Skills} turns the list into a {@code ToolProvider} with {@code activate_skill} and
 * {@code read_skill_resource}, plus the skill list for the system message
 * ({@link Skills#formatAvailableSkills()}). Spring AI 2.0 has no client-side counterpart; its
 * {@code AnthropicSkill} is the provider-hosted kind, which runs in Anthropic's container.
 */
@Configuration
public class SkillsConfig {

    static final String TRAVEL_INSURANCE = "travel-insurance";

    @Bean
    Skills travelSkills(InsuranceTools insuranceTools) {
        return Skills.from(ClassPathSkillLoader.loadSkills("skills").stream()
                .map(skill -> skill.name().equals(TRAVEL_INSURANCE) ? withTools(skill, insuranceTools) : skill)
                .toList());
    }

    private static Skill withTools(Skill skill, Object tools) {
        return Skill.builder()
                .name(skill.name())
                .description(skill.description())
                .content(skill.content())
                .resources(skill.resources())
                .tools(tools)
                .build();
    }
}
