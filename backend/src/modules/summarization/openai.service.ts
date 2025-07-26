import { Injectable, Logger } from '@nestjs/common';
import OpenAI from 'openai';
import { environment } from '@/environments/environment';

export interface SummarizationRequest {
  chatId: string;
  messages: Array<{
    sender: string;
    body: string;
    timestamp: Date;
  }>;
  periodStart: Date;
  periodEnd: Date;
}

export interface SummarizationResult {
  summary: string;
  tokensUsed: number;
  model: string;
}

@Injectable()
export class OpenAIService {
  private readonly logger = new Logger(OpenAIService.name);
  private readonly openai: OpenAI;

  constructor() {
    if (!environment.openai.apiKey) {
      throw new Error('OpenAI API key is required');
    }

    this.openai = new OpenAI({
      apiKey: environment.openai.apiKey,
    });
  }

  async summarizeMessages(request: SummarizationRequest): Promise<SummarizationResult> {
    this.logger.log(`Summarizing ${request.messages.length} messages for chat ${request.chatId}`);

    try {
      const prompt = this.buildPrompt(request);

      const completion = await this.openai.chat.completions.create({
        model: environment.openai.model,
        messages: [
          {
            role: 'system',
            content: 'You are a helpful assistant that creates concise, informative summaries of WhatsApp conversations. Focus on key topics, decisions, and important information while maintaining context.',
          },
          {
            role: 'user',
            content: prompt,
          },
        ],
        max_tokens: environment.openai.maxTokens,
        temperature: 0.3,
        presence_penalty: 0.1,
        frequency_penalty: 0.1,
      });

      const summary = completion.choices[0]?.message?.content?.trim();

      if (!summary) {
        throw new Error('No summary generated from OpenAI');
      }

      const result: SummarizationResult = {
        summary,
        tokensUsed: completion.usage?.total_tokens || 0,
        model: completion.model,
      };

      this.logger.log(`Summary generated for chat ${request.chatId}: ${result.tokensUsed} tokens used`);
      return result;

    } catch (error) {
      this.logger.error(`Failed to summarize messages for chat ${request.chatId}:`, error);
      throw error;
    }
  }

  private buildPrompt(request: SummarizationRequest): string {
    const formatDate = (date: Date) => date.toLocaleString();
    const timeRange = `${formatDate(request.periodStart)} - ${formatDate(request.periodEnd)}`;

    const messagesText = request.messages
      .map(msg => `[${formatDate(msg.timestamp)}] ${msg.sender}: ${msg.body}`)
      .join('\n');

    return `Please create a concise summary of this WhatsApp conversation from ${timeRange}.

Chat ID: ${request.chatId}
Number of messages: ${request.messages.length}

Conversation:
${messagesText}

Please provide a summary that includes:
1. Main topics discussed
2. Key decisions or conclusions reached
3. Important announcements or updates
4. Action items or follow-ups mentioned

Format the summary in markdown with clear sections. Keep it informative but concise (max 500 words).`;
  }

  async checkHealth(): Promise<boolean> {
    try {
      await this.openai.models.list();
      return true;
    } catch (error) {
      this.logger.error('OpenAI health check failed:', error);
      return false;
    }
  }
}
