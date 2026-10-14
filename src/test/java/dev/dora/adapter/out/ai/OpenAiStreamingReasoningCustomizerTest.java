package dev.dora.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Checks that the streaming OpenAI client receives the configured reasoning effort.
 *
 * @author Rodrigo Prestes Machado
 */
class OpenAiStreamingReasoningCustomizerTest {

    @Test
    void copiesReasoningEffortOntoTheStreamingModel() {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey("test")
                .modelName("gpt-6-luna");

        new OpenAiStreamingReasoningCustomizer(Optional.of("none")).customize(builder);

        assertEquals("none", reasoningEffort(builder));
    }

    @Test
    void leavesReasoningEffortUnsetWhenThePropertyIsBlank() {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey("test")
                .modelName("gpt-6-luna");

        new OpenAiStreamingReasoningCustomizer(Optional.of("  ")).customize(builder);

        assertNull(reasoningEffort(builder));
    }

    private static String reasoningEffort(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder) {
        try {
            Field field = OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder.class
                    .getDeclaredField("reasoningEffort");
            field.setAccessible(true);
            return (String) field.get(builder);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
