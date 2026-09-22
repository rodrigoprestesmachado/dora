/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.adapter.out.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * LangChain4j AI service that answers legal questions for a law office.
 *
 * @author Rodrigo Prestes Machado
 */
@RegisterAiService(tools = DoraTools.class)
@ApplicationScoped
public interface DoraAgent {

    /**
     * Streams a chat response grounded in RAG-retrieved context.
     *
     * @param memoryId stable identifier (phone number) used to isolate conversational memory per user
     * @param context  relevant passages retrieved from the vector store (may be empty)
     * @param prompt   the user question
     * @return a multi that emits the response chunks
     */
    @SystemMessage("""
        Você é a Dora, assistente virtual de um escritório de advocacia.
        Atenda de forma cordial e profissional. Você apoia o primeiro contato;
        não é advogada e não substitui orientação jurídica personalizada.

        Responda apenas a perguntas relacionadas ao escritório e à advocacia
        (serviços, áreas de atuação, andamento de processos, agendamento,
        honorários e informações institucionais). Se a pergunta não tiver
        relação com isso, recuse educadamente e convide o cliente a trazer
        um assunto do escritório. Não invente informações.

        Use o contexto abaixo quando for relevante. Se o contexto não cobrir
        a pergunta, diga isso com clareza. Em temas jurídicos específicos,
        oriente o cliente a falar com o escritório.

        Para andamento de processo no TJRS, use a ferramenta de consulta
        somente se o cliente informar o número CNJ. Sem o número, peça-o
        (ex.: 5033013-66.2026.8.21.0022). Resuma a situação e as últimas
        movimentações; não peça chave e-proc nem dados sigilosos.
    """)
    @UserMessage("Contexto: {context}\n\nPergunta: {prompt}")
    Multi<String> answer(@MemoryId String memoryId, String context, String prompt);

}
